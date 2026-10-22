# Design — Backend

## 1. Decisions at a glance

| Topic | Decision |
|-------|----------|
| Application shape | One Spring Boot app, two sides: `com.bandup.api` (backend) and `com.bandup.ui` (Thymeleaf frontend) |
| Frontend ↔ backend | Only over HTTP: the browser JS calls `/api/v1/**` with `Authorization: Bearer <JWT>` |
| Style | DDD with bounded contexts; layered architecture inside each context |
| Domain purity | Domain = JDK only. No Spring, JPA, Jackson, Lombok |
| Application layer | Use cases; may use only Spring `@Service` / `@Transactional` |
| Persistence | Separate JPA entities in infrastructure + mappers + repository adapters |
| Learner reads | Read models built with SQL (`JdbcClient`), bypassing the domain (CQRS-lite) |
| Cross-context data | Through ports owned by the caller (anti-corruption), never shared classes |
| Authentication | Email + password (BCrypt); access JWT (15 min, in browser memory) + rotating refresh token (14 days, HttpOnly cookie) |
| JWT library | Spring Security OAuth2 Resource Server (Nimbus): HS256, secret from config |
| HTML | jsoup: sanitizing (allow-list) and reading `data-g` / `ev` keys |
| Errors | Spring `ProblemDetail` (RFC 9457), `errors` list for 422 |
| DTOs | Java records, mapped by hand |
| Enforcement | ArchUnit tests |

---

## 2. The two sides

```
 Browser
 ┌───────────────────────────────────────────────┐
 │  HTML page (from ui)  +  JS modules           │
 │  auth.js: access token in memory              │
 │           apiFetch() adds "Bearer <JWT>"      │
 └───────┬────────────────────────────┬──────────┘
         │ GET /reading/{id}          │ fetch /api/v1/**  (JSON + JWT)
         ▼                            ▼
 ┌──────────────────┐        ┌───────────────────────────────────────┐
 │ com.bandup.ui    │        │ com.bandup.api                        │
 │ page controllers │        │ Spring Security (JWT filter)          │
 │ templates/static │   ✗    │  ├─ identity  ├─ practice  ├─ content │
 │ no business logic│ ─────▶ │  └─ shared    (catalog, media later)  │
 └──────────────────┘ never  └───────────────────┬───────────────────┘
                     imports                     ▼
                                           PostgreSQL (Flyway)
```

- **ui** serves only HTML shells and static files. All of them are public;
  pages contain no data. The navbar's "Admin" entry is shown by JS when the
  token has the ADMIN/AUTHOR role.
- **api** is stateless. Each request is authorised by its access token.

---

## 3. Layers inside each backend context

```
 web (REST controllers, JSON records)
   ▼
 application (use cases, transactions, ports for needed services)
   ▼
 domain (aggregates, value objects, domain services, repository ports)  ← JDK only
   ▲
 infrastructure (JPA entities + mappers + adapters, SQL read models, jsoup, BCrypt, JWT)
```

- Arrows are compile-time dependencies. Infrastructure **implements** domain
  and application ports; Spring wires the adapters in at runtime.
- `web` talks only to `application`.

---

## 4. Packages

```
com.bandup
├── api/
│   ├── shared/
│   │   ├── domain/           LanguageCode, LanguageChain, LanguageFallbacks, Translations<T>,
│   │   │                     DomainException, Violation
│   │   ├── application/      LanguageCatalog (port), CurrentUser (port)
│   │   ├── infrastructure/   JdbcLanguageCatalog, SecurityConfig, JWT encoder/decoder config
│   │   └── web/              ProblemDetail handler, RequestLanguage
│   ├── identity/
│   │   ├── domain/           User aggregate, Email, PasswordHash, Role, UserStatus,
│   │   │                     RefreshToken, UserRepository, RefreshTokenRepository (ports)
│   │   ├── application/      RegisterLearner, Login, RefreshSession, Logout, GetMe,
│   │   │                     BootstrapAdmin; ports: PasswordHasher, AccessTokenIssuer, TokenGenerator
│   │   ├── infrastructure/   JPA adapters, BCryptPasswordHasher, JwtAccessTokenIssuer,
│   │   │                     SecureRandomTokenGenerator
│   │   └── web/              AuthController (+ refresh cookie handling)
│   ├── practice/
│   │   ├── domain/           Attempt, Grader, GradingRule, Score, GivenAnswer, GradableExercise,
│   │   │                     AttemptRepository (port)
│   │   ├── application/      GetLearnerPassage, SubmitAttempt; ports: GradableExercises, LearnerPassages
│   │   ├── infrastructure/   SQL read models, JPA attempt entities + adapter
│   │   └── web/              PassageController, AttemptController
│   └── content/
│       ├── domain/           Exercise aggregate, value objects, QuestionNumbering, PublishPolicy,
│       │                     ExerciseRepository (port)
│       ├── application/      ListExercises, GetExerciseForEditing, CreateExercise, UpdateExercise,
│       │                     PublishExercise, UnpublishExercise, DeleteExercise;
│       │                     ports: HtmlSanitizer, MarkupInspector, AttemptsLookup
│       ├── infrastructure/   persistence/ (JPA + ExerciseMapper + adapter), html/ (jsoup)
│       └── web/              AdminExerciseController
└── ui/                       today's com.bandup.web, moved: HomeController, ReadingPageController,
                              AdminPageController, NavigationAdvice, … (no business logic)
```

`AdminAccess`, `AdminAccessFilter` and `AdminWebAdvice.isAdmin` (the dev
guard) are removed once Identity is in place; admin pages become public
shells and admin data is protected by the API.

---

## 5. Shared kernel

| Type | Layer | Rule |
|------|-------|------|
| `LanguageCode` | domain | non-blank BCP-47 code; equality case-insensitive, keeps stored spelling |
| `LanguageChain` | domain | ordered codes ending with `en`; `pick(Translations<T>)` |
| `LanguageFallbacks` | domain | builds chains: unknown region → base, unknown → `en`, loop-safe |
| `Translations<T>` | domain | immutable `LanguageCode → T`, one per language |
| `DomainException`, `Violation(field, message)` | domain | rule violations |
| `LanguageCatalog` | application port | code → fallback for all languages (`JdbcLanguageCatalog`) |
| `CurrentUser` | application port | the caller's user id + roles + support language, or anonymous (adapter reads the JWT) |

`RequestLanguage` (web) picks: `?lang=` / body → token's support language →
`Accept-Language` → `en`.

---

## 6. Identity context

### 6.1 Domain

```
User (root)
├── id: UserId              (internal)       ├── publicId: PublicUserId (UUID, in JWT/API)
├── email: Email            (lower-case, valid format)
├── passwordHash: PasswordHash (opaque; the domain never sees a raw password after hashing)
├── displayName             ├── uiLanguage / supportLanguage / targetLanguage: LanguageCode
├── roles: Set<Role>        (LEARNER, AUTHOR, TRANSLATOR, ADMIN)
└── status: UserStatus      (ACTIVE, DISABLED, DELETED) → canAuthenticate()

RefreshToken (root)
├── id, userId, tokenHash (SHA-256), familyId, issuedAt, expiresAt
├── replacedBy?  (set when rotated)          ├── revokedAt?
└── rules: isUsable(now); rotate(newToken); revokeFamily()
```

### 6.2 Flows

**Login**
```
POST /api/v1/auth/login {email, password}
  → find user by email → PasswordHasher.matches → user.canAuthenticate()
  → issue access JWT (15 min) + new refresh token family (14 days)
  ← 200 {accessToken, expiresIn, user}   + Set-Cookie: refresh=…; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth
```

**Refresh (every page load, since the access token lives in memory)**
```
POST /api/v1/auth/refresh   (cookie only)
  → hash cookie value → find token
     used before?  → revoke the whole family (stolen-token signal) → 401
     expired/revoked/user disabled → 401
  → rotate: mark old replaced, create new token in the same family
  ← 200 {accessToken, expiresIn, user} + new cookie
```

**Logout**: revoke the token's family, clear the cookie, `204`.

### 6.3 Access token (JWT)

| Claim | Value |
|-------|-------|
| `sub` | user's `publicId` |
| `roles` | e.g. `["ADMIN","AUTHOR"]` |
| `lang` | support language |
| `iat`, `exp` | issued / +15 min |
| `iss` | `bandup` |

Signed HS256 with `BANDUP_JWT_SECRET` (≥ 32 bytes, from env; tests use a
generated one). Spring Security's resource server validates it on every
`/api/v1/**` request.

### 6.4 Security rules

| Endpoint | Access |
|----------|--------|
| `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` | public |
| `GET /api/v1/auth/me` | any valid token |
| `GET /api/v1/passages/**`, `POST /api/v1/attempts` | public (token optional) |
| `/api/v1/admin/**` | role ADMIN or AUTHOR |
| everything else under `/api/v1/**` | denied |
| pages and static files (`ui`) | public |

CSRF: the API is token-based and stateless, so CSRF protection is off for
`/api/v1/**`; the refresh cookie is `SameSite=Strict` and only accepted on
`/api/v1/auth/*`. CORS: same origin only.

### 6.5 First admin

`BootstrapAdmin` runs at startup: if no user has ADMIN and
`BANDUP_ADMIN_EMAIL` / `BANDUP_ADMIN_PASSWORD` are set, it creates that admin.

---

## 7. Practice context

### 7.1 Domain

```
Attempt (root)
├── id: AttemptId (UUID)   ├── exerciseId   ├── exerciseRevision   ├── userId?
├── language: LanguageCode ├── elapsed: Duration
└── answers: List<GradedAnswer(questionId, number, given, expected: List<String>, score: Score)>
     score/total derived; percent computed
```

- `GradingRule(family, accepted, chooseCount?, wordLimit?)` and `Score(points,
  possible)` are value objects; `Grader` is a pure domain service
  (requirement 3.2).
- `GradableExercise` is Practice's **own** view of an exercise (per question:
  id, number, rule, evidence key, explanations), loaded through the port
  `GradableExercises`. Practice never imports Content classes.
- `Attempt.grade(GradableExercise, answers, language, userId?)` grades every
  question and builds the attempt.

### 7.2 Read model

`LearnerPassages` (port) returns a `PassageView` record. The adapter uses
explicit SQL that never selects from `question_answer` or
`question_explanation`, and leaves out evidence links (`database` design §5.1).

### 7.3 Use cases

| Use case | Flow |
|----------|------|
| `GetLearnerPassage(slug, lang)` | language chain → `LearnerPassages.find` → 404 if absent |
| `SubmitAttempt(command)` | validate numbers → `GradableExercises.find` (404 if not PUBLISHED) → `Attempt.grade` (user from `CurrentUser`, may be anonymous) → save → result |

---

## 8. Content context

### 8.1 Exercise aggregate

```
Exercise (root)
├── id: ExerciseId   ├── slug: Slug   ├── skill   ├── title   ├── format   ├── level?
├── timer: Timer?    ├── status       ├── revision: Revision  ├── version: long (concurrency token)
├── placement?: (stageId, position)   ├── topicIds: Set<TopicId>
├── passage?: Passage
│     ├── html: SanitizedHtml
│     ├── glosses: List<Gloss(key, surface, lemma?, meanings: Translations<GlossMeaning>)>
│     └── evidence: Set<EvidenceKey>
└── groups: List<QuestionGroup>
      ├── type: QuestionType(code, family)
      ├── instruction: SanitizedHtml, instructionTranslations: Translations<SanitizedHtml>
      ├── rules: chooseCount?, WordLimit?(maxWords, numbersAllowed), optionsReusable, mediaId?
      ├── options: List<Option>        (group bank, match family)
      └── questions: List<Question>
            ├── stem: Stem (Text | Gap(pre, post))     ├── evidenceKey?
            ├── options: List<Option>  (MCQ/MCMA)      ├── answerKey: AnswerKey
            └── explanations: Translations<SanitizedHtml>
```

Value objects validate themselves: `Slug`, `Timer`, `WordLimit`, `GlossKey`
(`g\d+`), `EvidenceKey` (`e\d+`), `OptionKey`.

### 8.2 Behaviour

| Method | Rule |
|--------|------|
| `Exercise.draft(...)` | new, DRAFT, revision 1 |
| `editContent(...)` | replaces content; if PUBLISHED → `revision++` |
| `syncMarkup(glossKeys, evidenceKeys)` | matches glosses/evidence to the HTML; fails if removed evidence is still used |
| `publish(PublishPolicy)` | all rules of requirement 4.2; throws with every violation |
| `unpublish()` / `archive()` | status changes; archive is final |
| `numbering()` | first number per question by order and points |

### 8.3 Ports and use cases

| Port | Adapter |
|------|---------|
| `ExerciseRepository` (domain) | `JpaExerciseRepository` |
| `HtmlSanitizer`, `MarkupInspector` (application) | jsoup adapters |
| `AttemptsLookup` (application) | SQL on `attempt` |

| Use case | Flow |
|----------|------|
| `ListExercises` / `GetExerciseForEditing` | summaries / full aggregate → admin DTO |
| `CreateExercise` | sanitize → draft → sync markup → save |
| `UpdateExercise(slug, version, data)` | load → version check → sanitize → `editContent` → sync markup → save |
| `PublishExercise` / `UnpublishExercise` | load → `publish(policy)` / `unpublish()` → save |
| `DeleteExercise` | has attempts ? `archive()` + save : delete |

Persistence: one JPA entity per table with `@Version` on `ExerciseEntity`;
`ExerciseMapper` maps both ways onto the managed entity graph (children
matched by position/key). A stale version becomes
`ConcurrentModificationException` → `409`.

---

## 9. REST API summary

| Method | Path | Access | Success |
|--------|------|--------|---------|
| POST | `/api/v1/auth/register` | public | 201 |
| POST | `/api/v1/auth/login` | public | 200 + cookie |
| POST | `/api/v1/auth/refresh` | refresh cookie | 200 + new cookie |
| POST | `/api/v1/auth/logout` | refresh cookie | 204 |
| GET | `/api/v1/auth/me` | token | 200 |
| GET | `/api/v1/passages/{slug}?lang=` | public | 200 |
| POST | `/api/v1/attempts` | public (token optional) | 201 |
| GET | `/api/v1/admin/passages` | ADMIN/AUTHOR | 200 |
| GET | `/api/v1/admin/passages/{slug}` | ADMIN/AUTHOR | 200 |
| POST | `/api/v1/admin/passages` | ADMIN/AUTHOR | 201 |
| PUT | `/api/v1/admin/passages/{slug}` | ADMIN/AUTHOR | 200 |
| DELETE | `/api/v1/admin/passages/{slug}` | ADMIN/AUTHOR | 204 |
| POST | `/api/v1/admin/passages/{slug}/publish` | ADMIN/AUTHOR | 200 |
| POST | `/api/v1/admin/passages/{slug}/unpublish` | ADMIN/AUTHOR | 200 |

### Error mapping (`api.shared.web`)

| Cause | Status |
|-------|--------|
| no / invalid / expired token | 401 |
| token without the required role | 403 |
| not found / not published | 404 |
| malformed request, unknown question number | 400 |
| `PublishPolicy` / value-object violations | 422 + `errors[]` |
| stale version, duplicate email | 409 |
| named DB constraint (e.g. `question_evidence_same_exercise_fk`) | 422/409 with a clear message |

---

## 10. What the frontend will need (UI tasks, not in this spec)

- `auth.js`: login form handling, access token kept in memory, silent
  `POST /api/v1/auth/refresh` on page load, `apiFetch()` adding the Bearer
  header and retrying once after a refresh on `401`, logout.
- Login and register pages (Thymeleaf shells in `ui`).
- Admin pages: redirect to login when the API answers `401`/`403`.

---

## 11. Testing strategy

| Layer | How | Spring? |
|-------|-----|---------|
| domain | plain JUnit: value objects, aggregates, `PublishPolicy`, `Grader`, numbering, fallbacks, refresh-token rotation rules | no |
| application | JUnit with in-memory fakes for ports | no |
| infrastructure | Testcontainers PostgreSQL: mapper round-trips, adapters, SQL read models; jsoup, BCrypt, JWT adapters | slice |
| web | MockMvc + Testcontainers per endpoint, including 401/403 per role | yes |
| architecture | ArchUnit (§12) | no |

Grading uses a shared case file (`src/test/resources/grading-cases.json`).

## 12. ArchUnit rules

1. `com.bandup.ui..` does not depend on `com.bandup.api..`.
2. `..api..domain..` depends only on `java..` and `..domain..` classes.
3. `..api..application..` depends only on `java..`, `..domain..`,
   `..application..`, `org.springframework.stereotype..`,
   `org.springframework.transaction..`.
4. `..api..web..` does not depend on `..infrastructure..`.
5. No context depends on another context's `domain`/`application`/
   `infrastructure` (only `api.shared` may be used by all).
6. No `@Entity` outside `..infrastructure..`.

---

## 13. Decisions (resolved questions)

1. **Gloss keys** (`g1`, `g2`, …) are allocated by the **admin editor** when a
   word is marked. The server only validates them (`g\d+`, unique within the
   passage) and never renumbers them.
2. **Email verification and password reset**: later, not in phase 1.
3. **Phase 2**: Catalog (browse/filter) first. **Media is postponed** until
   after the first production release (no Listening yet). Until then
   `DIAGRAM_LABEL` exercises can't be published, because `PublishPolicy`
   requires an image.

### Still open (UI side, separate tasks)

- The admin editor still writes `data-az` and has no per-language meanings; it
  needs a UI update to use this API.
- `static/js/reading/fixtures.js` and `static/js/admin/fixtures.js` contain
  answer keys and are publicly served; remove them when the UIs switch to the
  API.
