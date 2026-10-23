# Design — Backend

## 1. Decisions at a glance

| Topic | Decision |
|-------|----------|
| Application shape | One Spring Boot app, two sides: `com.bandup.api` (backend) and `com.bandup.ui` (Thymeleaf frontend) |
| Frontend ↔ backend | Only over HTTP: the browser JS calls `/api/v1/**` with `Authorization: Bearer <JWT>` |
| Style | DDD: strategic (bounded contexts) **and** tactical (aggregates, entities, value objects, repositories, domain services); layered architecture inside each context |
| Domain layout | One module per aggregate, split by building block: `aggregate/`, `entity/`, `valueobject/`, `repository/`, `service/`, `exception/` (§4) |
| Building blocks in code | Plain-Java markers `@AggregateRoot`, `@Entity`, `@ValueObject`, `@DomainService` (`shared.domain.ddd`) on every domain class |
| Aggregates | Changed only through their root; reference each other only by id; equal by identity; one repository per aggregate, storing the whole aggregate |
| Domain purity | Domain = JDK only. No Spring, JPA, Jackson, Lombok |
| Application layer | Use cases; may use only Spring `@Service` / `@Transactional`. Slow work (BCrypt) never runs inside a transaction |
| Persistence | Separate JPA entities in infrastructure + mapping + repository adapters; entities are unproxied before any field is read or written |
| Concurrency | An aggregate is also the locking unit (`SELECT … FOR UPDATE`); admin edits carry the exercise's `version`, checked and raised under that lock (stale → 409) |
| Time | Only from the injected `java.time.Clock`; no `Instant.now()` in main code |
| Learner reads | Read models built with SQL (`JdbcClient`), bypassing the domain (CQRS-lite) |
| Cross-context data | Through ports owned by the caller (anti-corruption), never shared classes |
| Authentication | Email + password (BCrypt cost 12); access JWT (15 min, in browser memory) + rotating refresh token (14 days, HttpOnly cookie `bandup_refresh`) |
| JWT library | Spring Security OAuth2 Resource Server (Nimbus): HS256, secret `BANDUP_JWT_SECRET` (random dev key if unset) |
| Security rules | Each context declares its own endpoints' rules (`ApiAccessRules`); the shared kernel knows no context URLs |
| HTML | jsoup: sanitizing (allow-list) and reading `data-g` / `ev` keys |
| Errors | Spring `ProblemDetail` (RFC 9457) for every error, incl. filter 401/403; `errors` list for 422 |
| DTOs | Java records, mapped by hand |
| Enforcement | ArchUnit: 13 rules (§12), each proven by a planted-violation run |

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
 domain (one module per aggregate: aggregate/ entity/ valueobject/ repository/ service/)  ← JDK only
   ▲
 infrastructure (JPA entities + mappers + adapters, SQL read models, jsoup, BCrypt, JWT)
```

- Arrows are compile-time dependencies. Infrastructure **implements** domain
  and application ports; Spring wires the adapters in at runtime.
- `web` talks only to `application`.

---

## 4. Packages

Built so far (shared kernel, Identity); planned (Practice, Content) follow the
same shape. The tactical model of each new context is presented for approval
before its first task.

```
com.bandup
├── api/
│   ├── shared/                               shared kernel
│   │   ├── domain/
│   │   │   ├── language/     LanguageCode, LanguageChain, Translations (@ValueObject),
│   │   │   │                 LanguageFallbacks (@DomainService)
│   │   │   ├── numbering/    QuestionNumbering (@DomainService), NumberRange (@ValueObject)
│   │   │   ├── error/        DomainException, Violation (@ValueObject)
│   │   │   └── ddd/          @AggregateRoot, @Entity, @ValueObject, @DomainService
│   │   ├── application/      LanguageCatalog, CurrentUser (ports); NotFound-, InvalidRequest-,
│   │   │                     Conflict-, Unauthenticated-, ForbiddenException
│   │   ├── infrastructure/   JdbcLanguageCatalog, ClockConfig
│   │   │   └── security/     SecurityConfig, ApiAccessRules (extension point), JwtConfig,
│   │   │                     JwtProperties, AccessTokenClaims, JwtCurrentUser
│   │   └── web/              ApiExceptionHandler, ApiProblems, RequestLanguage
│   ├── identity/
│   │   ├── domain/
│   │   │   ├── user/
│   │   │   │   ├── aggregate/      User
│   │   │   │   ├── valueobject/    UserId, Email, NewPassword, PasswordHash, Role, UserStatus
│   │   │   │   └── repository/     UserRepository
│   │   │   └── session/
│   │   │       ├── aggregate/      Session
│   │   │       ├── entity/         RefreshToken
│   │   │       ├── valueobject/    SessionId, TokenHash
│   │   │       └── repository/     SessionRepository
│   │   ├── application/      RegisterLearner, Login, RefreshSession, Logout, GetMe, BootstrapAdmin,
│   │   │                     SessionIssuer; results SignedIn, UserView
│   │   │   └── port/         PasswordHasher, AccessTokenIssuer, TokenGenerator, AuthSettings
│   │   ├── infrastructure/
│   │   │   ├── persistence/  UserEntity, RefreshTokenEntity, Spring Data repos,
│   │   │   │                 JpaUserRepository, JpaSessionRepository
│   │   │   ├── security/     BCryptPasswordHasher, SecureRandomTokenGenerator,
│   │   │   │                 JwtAccessTokenIssuer, IdentityAccessRules
│   │   │   └── config/       AuthProperties, IdentityConfig, BootstrapAdminRunner
│   │   └── web/              AuthController, AuthRequests, AuthResponse, RefreshCookie
│   ├── practice/             (§7; built: domain, adapters, use cases, learner API)
│   │   ├── domain/
│   │   │   └── attempt/
│   │   │       ├── aggregate/      Attempt
│   │   │       ├── valueobject/    AttemptId, ExerciseId, QuestionId, LearnerId, ExerciseRevision,
│   │   │       │                   AnswerSheet, GivenAnswer, GradedAnswer, Score, GradableExercise,
│   │   │       │                   GradableQuestion, GradingRule, QuestionFamily, WordLimit
│   │   │       ├── service/        Grader
│   │   │       ├── exception/      InvalidSubmissionException
│   │   │       └── repository/     AttemptRepository
│   │   ├── application/      GetLearnerPassage, SubmitAttempt; PassageView (read model), AttemptResult
│   │   │   └── port/         LearnerPassages, GradableExercises
│   │   └── infrastructure/
│   │       ├── readmodel/    JdbcLearnerPassages, JdbcGradableExercises (SQL, JdbcClient)
│   │       ├── persistence/  AttemptEntity, AttemptAnswerRow (attempt_answer, jsonb), AttemptJson,
│   │       │                 JpaAttemptRepository
│   │       └── security/     PracticeAccessRules
│   │   └── web/              PassageController, PassageResponse, AttemptController, AttemptResponse
│   ├── catalog/              (§8b; read-only, no domain layer)
│   │   ├── application/      BrowseExercises, CatalogQuery, CatalogPage, ExerciseCard, CatalogFilters
│   │   │   └── port/         ExerciseCatalog
│   │   ├── infrastructure/   JdbcExerciseCatalog, CatalogAccessRules
│   │   └── web/              CatalogController
│   └── content/              (§8; built: domain, persistence, HTML adapters, use cases, admin API)
│       └── domain/
│           └── exercise/
│               ├── aggregate/      Exercise
│               ├── entity/         Passage, Gloss, QuestionGroup, Question
│               ├── valueobject/    ExerciseId, GroupId, QuestionId, Slug, Skill, Format, Level,
│               │                   ExerciseStatus, Revision, Timer, Placement, StageId, TopicId,
│               │                   MediaId, SanitizedHtml, MarkupKeys, GlossKey, GlossMeaning,
│               │                   EvidenceKey, QuestionType, QuestionFamily, WordLimit, Option,
│               │                   OptionKey, Stem, AnswerKey, Explanation, TextSource, GlossNote,
│               │                   ExerciseContent
│               ├── service/        PublishPolicy
│               └── repository/     ExerciseRepository
│       ├── application/      ExerciseAdmin (the 7 use cases), ExerciseContentBuilder, ExerciseInput,
│       │   │                 AdminExerciseView, ExerciseViews, ExerciseSummary
│       │   └── port/         HtmlSanitizer, MarkupInspector, QuestionTypes, ReferenceData,
│       │                     AttemptsLookup, ExerciseSummaries
│       └── infrastructure/
│           ├── html/         JsoupHtmlSanitizer, JsoupMarkupInspector
│           ├── persistence/  one plain JPA entity per table (ExerciseEntity, ExerciseTopicEntity,
│           │                 ReadingPassageEntity, PassageGlossEntity, PassageGlossTranslationEntity,
│           │                 PassageEvidenceEntity, QuestionGroupEntity, QuestionGroupTranslationEntity,
│           │                 QuestionEntity, QuestionOptionEntity, QuestionAnswerEntity,
│           │                 QuestionExplanationEntity), JpaExerciseRepository
│           ├── readmodel/    JdbcQuestionTypes, JdbcReferenceData, JdbcAttemptsLookup,
│           │                 JdbcExerciseSummaries
│           └── security/     ContentAccessRules
│       └── web/              AdminExerciseController
└── ui/                       page shells: HomeController, ReadingPageController, AuthPageController
                              (/login, /register), NavItem, NavigationAdvice; admin/: AdminPageController,
                              AdminNavItem, AdminNavigationAdvice — no data, no API imports
```

The former dev guard (`AdminAccess`, `AdminAccessFilter`,
`app.admin.dev-allow`) and all old page controllers have been removed; admin
data is protected only by the API's role rules.

---

## 5. Shared kernel

| Type | Where | Rule |
|------|-------|------|
| `LanguageCode` | domain.language | BCP-47 shape; equality case-insensitive, keeps stored spelling; `base()` |
| `LanguageChain` | domain.language | non-empty, no repeats, always ends with `en`; `pick`, `pickAll` |
| `LanguageFallbacks` | domain.language | builds chains: unknown region → base, unknown/malformed/blank → `en`, loop-safe, stops at `en`; `isKnown`, `storedSpelling` |
| `Translations<T>` | domain.language | immutable `LanguageCode → T`, one per language |
| `QuestionNumbering`, `NumberRange` | domain.numbering | numbers from order and points: a "Choose N" question takes N numbers ("7–8"). Shared because the learner view, the attempt and the admin editor must number identically |
| `DomainException`, `Violation(field, message)` | domain.error | rule violations; carries every violation found |
| `@AggregateRoot` / `@Entity` / `@ValueObject` / `@DomainService` | domain.ddd | building-block markers (plain Java) |
| `LanguageCatalog` | application port | all languages + fallbacks (`JdbcLanguageCatalog`) |
| `CurrentUser` | application port | the caller's public id, roles, support language, or anonymous (`JwtCurrentUser`) |
| outcome exceptions | application | `InvalidRequestException` 400, `UnauthenticatedException` 401, `ForbiddenException` 403, `NotFoundException` 404, `ConflictException` 409 |
| `ApiAccessRules` | infrastructure.security | extension point: each context adds its endpoints' rules and token-free endpoints |
| `AccessTokenClaims` | infrastructure.security | the custom claim names (`roles`, `lang`), defined once |
| `ApiExceptionHandler`, `ApiProblems` | web | every error as Problem Details (§9) |

`RequestLanguage` (web) picks: `?lang=` / body → signed-in user's support
language → first `Accept-Language` tag (`*` skipped) → none (= `en`).

---

## 6. Identity context

### 6.1 Domain (tactical model)

```
user/                                         ← User aggregate
  aggregate/User            @AggregateRoot   equal by UserId
    id: UserId (public UUID; the JWT sub — the DB's numeric id never leaves persistence)
    email: Email (trimmed, lower-case)        passwordHash: PasswordHash (never printed)
    displayName? (≤ 80)                       ui/support/targetLanguage: LanguageCode
    roles: Set<Role>                          status: UserStatus
    registerLearner(..) · createAdmin(..) · restore(..) · canAuthenticate() (ACTIVE only)
    canAuthor() (ADMIN or AUTHOR) · changePassword(..) · disable() · markDeleted()
  valueobject/  UserId, Email, NewPassword (≥ 10 chars, ≤ 72 bytes), PasswordHash, Role, UserStatus
  repository/   UserRepository: findById, findByEmail, existsByEmail, existsWithRole, save

session/                                      ← Session aggregate (one login session)
  aggregate/Session          @AggregateRoot   equal by SessionId; references the user only by UserId
    tokens: List<RefreshToken> (issue order)
    start(userId, firstHash, now, lifetime) · restore(id, userId, tokens)
    present(hash, nextHash, now, lifetime) → Rotated(next) | Rejected(EXPIRED | REVOKED | REUSED)
        USABLE   → token copy marked replaced, new token added
        REPLACED → token was copied: revoke the WHOLE session (incl. the newest token)
        EXPIRED / REVOKED → rejected, nothing changes
    revoke(now) · isRevoked() · token(hash) · newestToken()
    invariants: ≥ 1 token, no duplicate hashes, every successor inside the session
  entity/RefreshToken        @Entity          equal by TokenHash; IMMUTABLE
    stateAt(now): REVOKED → REPLACED → EXPIRED → USABLE (in this order)
    withReplacedBy(next) / revoked(now) return copies; only Session swaps them into its list
  valueobject/  SessionId (stored as family_id), TokenHash (64 lower-case hex SHA-256)
  repository/   SessionRepository: findByTokenHash (whole session, locked), save (whole session)
```

### 6.2 Flows

**Register** (`RegisterLearner`, 201): validates every field at once (422
with all errors), unknown languages rejected, duplicate email 409. Doesn't sign
in; the UI calls login next. BCrypt runs outside any transaction.

**Login** (`Login` → `SessionIssuer.start`)
```
POST /api/v1/auth/login {email, password}
  → find user by email (malformed email = unknown)
  → PasswordHasher.matches against the user's hash, or a dummy hash if unknown (same timing)
  → wrong email / wrong password / not ACTIVE → the same 401 "Wrong email or password."
  → Session.start → save → access JWT (15 min)
  ← 200 {accessToken, tokenType: "Bearer", expiresIn, user}
    + Set-Cookie: bandup_refresh=…; Path=/api/v1/auth; Max-Age=1209600; Secure; HttpOnly; SameSite=Strict
```
No use-case transaction: BCrypt (~250 ms) never holds a DB connection; the
user lookup and the session insert each run in their own short transaction.

**Refresh** (`RefreshSession`; every page load, since the access token lives in memory)
```
POST /api/v1/auth/refresh   (cookie only; a stale Authorization header is ignored)
  → hash cookie → SessionRepository.findByTokenHash (whole session, LOCKED)
  → user missing or not ACTIVE → session.revoke → save → 401
  → session.present(hash, nextHash) → save
       Rotated  → 200 {accessToken, …} + new cookie
       Rejected → 401 + cookie cleared
```
One transaction with `noRollbackFor = UnauthenticatedException`, so a
revocation is committed even though the request answers 401. The adapter
locks every row of the session twice: a `FOR UPDATE` that had to wait can't
see rows inserted meanwhile (READ COMMITTED), but once the session's rows are
held no one can add more, so the second read is complete. This makes
concurrent uses of one session run one after another (a copied old token
racing the real user's new one always ends with the whole session revoked).

**Logout** (`Logout`, 204): revokes the whole session, clears the cookie;
idempotent (missing or unknown cookie is fine).

**Me** (`GetMe`): the current user's view; a token of a user who is no
longer ACTIVE gets 401. `UserView`: id, displayName, roles, ui/support/target
language — no email, no hash, no internal id.

### 6.3 Access token (JWT)

| Claim | Value |
|-------|-------|
| `sub` | the user's public id (`UserId`) |
| `roles` | e.g. `["ADMIN","AUTHOR"]` (`AccessTokenClaims.ROLES`) |
| `lang` | support language (`AccessTokenClaims.LANGUAGE`) |
| `iat`, `exp` | issued / +15 min |
| `iss` | `bandup` |

Signed HS256 with `BANDUP_JWT_SECRET` (≥ 32 bytes, else startup fails). If
unset (local dev) a random key is generated at startup with a warning: tokens
then don't survive a restart. The decoder checks signature, expiry (Spring's
default 60 s clock skew) and issuer. `SignedIn` carries `issuedAt`, so
`expiresIn` and the cookie's Max-Age are computed without reading the time.

### 6.4 Security rules

Each context declares its endpoints through `ApiAccessRules`;
`SecurityConfig` applies them all, then denies any other `/api/**`.

| Endpoint | Access | Declared by |
|----------|--------|-------------|
| `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout` | public; **ignore** the `Authorization` header | `IdentityAccessRules` |
| `GET /api/v1/auth/me` | any valid token | `IdentityAccessRules` |
| `GET /api/v1/passages/**`, `POST /api/v1/attempts` | public (token optional) | `PracticeAccessRules` |
| `/api/v1/admin/**` | role ADMIN or AUTHOR | `ContentAccessRules` |
| everything else under `/api/**` | denied (401 anonymous, 403 signed in) | `SecurityConfig` |
| pages and static files (`ui`) | public | `SecurityConfig` |

An invalid or expired token is rejected (401) even on public endpoints: the
browser should refresh it, not silently become anonymous. Filter 401/403 go
through `ApiExceptionHandler` (Problem Details, `WWW-Authenticate: Bearer`).
CSRF is off (stateless; the refresh cookie is SameSite=Strict and only sent to
`/api/v1/auth`). No server sessions. CORS: same origin only.

### 6.5 First admin

`BootstrapAdminRunner` calls `BootstrapAdmin` at startup when
`BANDUP_ADMIN_EMAIL` is set: creates that admin (`BANDUP_ADMIN_PASSWORD`,
same password rules) only if no ADMIN exists; never turns an existing account
into an admin (logs a warning instead); an invalid configured password stops
startup.

### 6.6 Application and infrastructure

| Piece | Layer | Notes |
|-------|-------|-------|
| `RegisterLearner`, `Login`, `RefreshSession`, `Logout`, `GetMe`, `BootstrapAdmin` | application | use cases |
| `SessionIssuer` | application | builds `SignedIn` for login (new session) and refresh (rotated) |
| `PasswordHasher`, `AccessTokenIssuer`, `TokenGenerator`, `AuthSettings` | application.port | ports |
| `BCryptPasswordHasher` (cost 12), `SecureRandomTokenGenerator` (32 random bytes, base64url; SHA-256 hex), `JwtAccessTokenIssuer` | infrastructure.security | adapters |
| `JpaUserRepository` (`user_credential` as a secondary table), `JpaSessionRepository` | infrastructure.persistence | adapters; unproxy every entity |
| `AuthProperties` (`bandup.auth.*`: refresh lifetime 14d, admin email/password) | infrastructure.config | implements `AuthSettings` |

---

## 7. Practice context

> Built: tactical model (task 0), domain (task 1), adapters (task 2), use
> cases and learner API (task 3).

### 7.1 Domain (tactical model)

Practice owns one aggregate: the **Attempt**. The exercise it grades belongs
to Content; Practice gets its own read-only copy (`GradableExercise`, a value
object) through an application port and never imports Content classes.

```
attempt/                                      ← Attempt aggregate (one graded submission)
  aggregate/Attempt         @AggregateRoot   equal by AttemptId; IMMUTABLE (history is never rewritten)
    id: AttemptId (UUID)    exercise: ExerciseId    revision: ExerciseRevision
    learner?: LearnerId (anonymous if empty)       language: LanguageCode (support language)
    elapsed: Duration (≥ 0) submittedAt: Instant
    answers: List<GradedAnswer>  (one per question of the exercise, in number order)
    score() · total() · percent() (rounded half up)  derived from the answers, never stored apart
    grade(GradableExercise, AnswerSheet, language, learner?, elapsed, now, Grader)   new AttemptId
        any number of a question's range identifies it (Q7 or Q8 of a "Choose TWO")
        unknown number, two answers to one question, negative elapsed
            → InvalidSubmissionException with every problem
        unanswered question → given blank, 0 points
    restore(..)   invariants: ≥ 1 answer, no question twice, elapsed ≥ 0
  valueobject/
    AttemptId (UUID, generated in the domain)
    ExerciseId, QuestionId             Content's ids, as Practice's own value objects
    LearnerId                          the user's public id (UUID); Identity's UserId is never imported
    ExerciseRevision                   the revision that was answered
    AnswerSheet                        question number → GivenAnswer, as submitted; its builder
                                       rejects repeated numbers and numbers < 1
    GivenAnswer                        Text(value) | Keys(option keys); stored as given, never normalized
    GradedAnswer(question, number, given, expected, score)   expected = answer snapshot at grading time
    Score(points, possible)            0 ≤ points ≤ possible, possible ≥ 1
    GradableExercise(id, revision, questions in order)   Practice's view of a PUBLISHED exercise;
                                       numbers() via the shared QuestionNumbering, questionNumbered(n)
    GradableQuestion(id, rule, evidenceKey?, explanations: Translations<String>)
    GradingRule                        per-family factories: choiceFixed, gap(accepted, wordLimit?),
                                       mcSingle, mcMulti(keys, N) (exactly N keys), match; points() = N or 1
    QuestionFamily                     CHOICE_FIXED, GAP, MC_SINGLE, MC_MULTI, MATCH
    WordLimit(maxWords, numbersAllowed)
  service/Grader            @DomainService   GradingRule × GivenAnswer → Score (requirement 3.2)
    case-insensitive, trimmed, inner spaces collapsed · gap over the word limit = 0
    (numbers like 17, 1995, 3.5, 1,200, 10:30 not counted when allowed) · blank = 0
    "Choose N": 1 point per correct key up to N, more than N keys = 0, a repeated key counts once
  exception/InvalidSubmissionException      every problem found; the use case turns it into 400
  repository/AttemptRepository              save (whole attempt with its answers)
```

Numbers come from the shared `QuestionNumbering` (§5), so the learner view
and the attempt always number questions the same way. The attempt stores a
question's first number (`attempt_answer.question_number`, 7 for "Q7–8").

### 7.2 Read model

`LearnerPassages.find(slug, LanguageChain)` (application port) returns a
`PassageView` record, or empty unless the exercise is PUBLISHED and READING.
It's a read model, not a domain object (CQRS-lite). `JdbcLearnerPassages`
uses explicit SQL that never touches `question_answer`,
`question_explanation` or evidence links; a test reads every query of the
class and every field of `PassageView` to keep it that way.

```
PassageView  slug, language (chain's first), direction (ltr/rtl of that language), title,
             durationSeconds?, lowTimeThresholdSeconds?, stageTitle?, position?,
             passageHtml, glosses: key → meaning, groups
  Group      type, firstNumber, lastNumber, instruction, instructionTranslated?,
             maxWords?, numbersAllowed, chooseCount? (MCMA only), optionsReusable,
             options (group bank), questions
  Question   n, nTo? (only when it takes several numbers), type, text? | pre? + post?, options (own)
  Option     key, label
```

- Each piece of help text (gloss meaning, translated instruction, stage
  title) is resolved on its own along the chain; a gloss with no meaning in
  the chain is left out.
- Numbers come from the shared `QuestionNumbering` (points: `choose_count`
  for MCMA, else 1), so they match the attempt's numbers.
- Interface wording is **not** in the read model: the web layer (task 3)
  builds `stageLabel` ("Mərhələ 1 · Mini passage 4") from `stageTitle` +
  `position`, and `heading` ("Questions 1–3") from the group's numbers.
- Media (`DIAGRAM_LABEL` images) is left out until Media is built (§13.3).

`GradableExercises.findPublished(slug)` → `JdbcGradableExercises`: four
queries (exercise, questions in order with family / group rules / evidence
key, answers in order, explanations per language) → `GradableExercise`. A
published question whose rows break a grading rule (no answer, two answers
for TFNG, "Choose TWO" with three keys) fails loudly instead of being graded
wrongly.

**Attempt persistence** (`JpaAttemptRepository`): `attempt` + its
`attempt_answer` rows (an element collection, ordered by number), inserted
once in one transaction; saving the same attempt again is refused. Given
values are stored as JSON exactly as given (`"FALSE"`, `["A","C"]`), the
expected snapshot as a JSON array. The learner is stored as `app_user.id`,
looked up by public id with plain SQL (an unknown learner is refused);
`started_at` isn't tracked yet and `is_correct` is generated by the database.

### 7.3 Application

| Piece | Kind | Notes |
|-------|------|-------|
| `GetLearnerPassage(slug, requestedLanguage)` | use case | language chain → `LearnerPassages.find` → `NotFoundException` (404) if absent |
| `SubmitAttempt(command)` | use case, one transaction | collect shape problems (missing time or number, number < 1, repeated number) → `GradableExercises.findPublished` (404 if not PUBLISHED) → `Attempt.grade` (learner from `CurrentUser`, may be anonymous; its problems — unknown number, a question answered twice, negative time — are added) → any problem: `InvalidRequestException` (400) listing **all** of them → save → `AttemptResult` |
| `LearnerPassages`, `GradableExercises` | ports | SQL adapters (`JdbcClient`) |
| `AttemptRepository` | domain port | JPA adapter; `LearnerId` → `app_user.id` via `public_id`; flushes on save |

The requested language is picked in the web layer (`RequestLanguage`:
`?lang=` → token's support language → `Accept-Language` → English) and passed
to the use cases; the attempt stores the chain's first language as
`support_language`.

### 7.4 Learner API (`practice.web`)

**`GET /api/v1/passages/{slug}?lang=`** → `PassageResponse` (`reading-practice-ui`
§4.1 extended per `database` §5.1); empty values are left out:
`id, language, direction, stageLabel, position, title, durationSeconds,
lowTimeThresholdSeconds, passageHtml, glosses{gN: meaning},
questionGroups[{type, heading, instruction, instructionTranslated, maxWords,
numbersAllowed, chooseCount, optionsReusable, options[{key, label}],
questions[{n, nTo, type, text | pre + post, options}]}]`.
`stageLabel` is the stage title in the help language ("Mərhələ 1"); the UI
adds its own wording with `position` ("Mini passage 4"). `heading` is
"Questions 1–3" / "Question 9".

**`POST /api/v1/attempts?lang=`** body `{passageId, elapsedSeconds,
answers[{n, value}]}` where `value` is a string, a list of keys ("Choose N")
or null; anything else is 400. Extra fields (a client "score") are ignored.
→ 201 `{attemptId, score, total, percent, elapsedSeconds, results[{n, nTo,
correct, points, possible, given, answer, acceptedAnswers, explanation,
evidenceId}]}`. `given` is exactly what was sent (`""` when unanswered);
`answer` is the first accepted value, or the keys joined ("A, D") for
"Choose N".

---

## 8. Content context

> Built: tactical model (task 0), domain (task 1), persistence (task 2),
> HTML adapters (task 3), use cases and admin API (task 4).

### 8.1 Domain (tactical model)

Content owns one aggregate: the **Exercise**, edited and saved as a whole by
the admin editor. Topics, stages, media and question types are reference
data, referenced only by id/code (catalog management is phase 2).

```
exercise/                                     ← Exercise aggregate
  aggregate/Exercise        @AggregateRoot   equal by ExerciseId
    id: ExerciseId          slug: Slug (public id, unique)       skill: Skill
    targetLanguage: LanguageCode            title             format: Format    level?: Level
    timer?: Timer           placement?: Placement(StageId, position)   topics: Set<TopicId>
    status: ExerciseStatus  revision: Revision    version: long (optimistic lock, kept for persistence)
    passage?: Passage       groups: List<QuestionGroup>
    draft(id, slug, skill, targetLanguage, ExerciseContent)   new, DRAFT, revision 1
    editContent(ExerciseContent, PublishPolicy)   replaces title, format, level, timer, placement,
                                       topics, passage and groups (children keep their ids);
                                       PUBLISHED → must still pass the PublishPolicy, revision + 1;
                                       ARCHIVED can't be edited
    publish(PublishPolicy, now)        every rule of requirement 4.2, all violations at once;
                                       sets publishedAt on the first publish; again = no change
    unpublish() → DRAFT · archive() → ARCHIVED (final; publish/unpublish/edit then refused)
    numbering()                        QuestionId → NumberRange (shared QuestionNumbering)
    restore(..)                        for persistence; broken stored data → IllegalArgumentException
    Rules on EVERY save (drafts too), all reported at once:
      title required (≤ 200) · gloss keys g+digits and unique · evidence ids e+digits and unique ·
      a question's evidence must be in the passage (so removing used evidence is rejected) ·
      chooseCount only on MCMA (≥ 1), word limit only on gap, a bank only on matching,
      own options only on MCQ/MCMA · option keys unique per owner · ids unique
    Markup sync: glosses = the HTML's data-g keys in document order (editor's keys kept, never
      renumbered), surface word taken from the HTML, lemma/meanings from the editor's GlossNote
      by key (notes for unmarked keys dropped); evidence = the HTML's ev ids.
    Every failed change throws one DomainException and leaves the exercise unchanged.
  entity/
    Passage        @Entity  one per exercise    html: SanitizedHtml, wordCount,
                                                glosses: List<Gloss>, evidence: Set<EvidenceKey>
    Gloss          @Entity  equal by GlossKey   surface, lemma?, meanings: Translations<GlossMeaning>
    QuestionGroup  @Entity  equal by GroupId    type: QuestionType, instruction: SanitizedHtml,
                                                instructionTranslations: Translations<SanitizedHtml>,
                                                chooseCount?, wordLimit?, optionsReusable, media?: MediaId,
                                                bank: List<Option> (match family), questions: List<Question>
    Question       @Entity  equal by QuestionId stem: Stem, evidence?: EvidenceKey,
                                                options: List<Option> (MCQ/MCMA), answer: AnswerKey,
                                                explanations: Translations<Explanation>
    All immutable: only Exercise builds new ones and swaps them in (like RefreshToken in Identity).
  valueobject/
    ExerciseId, GroupId, QuestionId            ids (see 8.2)
    Slug, Skill, Format, Level, ExerciseStatus, Revision
    Timer(duration, lowTimeWarning?)           Placement(StageId, position)   StageId, TopicId, MediaId
    SanitizedHtml                              HTML that went through the HtmlSanitizer port
    MarkupKeys(glosses: [GlossMark(key, surface)], evidence: [id])   found in the sanitized HTML by
                                               the MarkupInspector port, raw (the root validates them)
    GlossNote(lemma?, meanings)                the editor's data for one gloss key
    ExerciseContent                            everything one editor save carries
    GlossKey (g\d+), EvidenceKey (e\d+), OptionKey, Option(key, label)
    GlossMeaning(text, source, reviewed), Explanation(html, source, reviewed), TextSource (HUMAN, MACHINE)
    QuestionType(code, family), QuestionFamily, WordLimit(maxWords, numbersAllowed)
    Stem                                       Text(text) | Gap(pre, post)
    AnswerKey                                  accepted values (gap), key(s) (choice/match)
  service/PublishPolicy     @DomainService   requirement 4.2, check(exercise) → all violations:
                                             reading has passage text · ≥ 1 question · every group has
                                             questions and an instruction · every stem has text ·
                                             TFNG/YNNG: one value of the fixed set · gap: ≥ 1 accepted,
                                             each within the word limit · MCQ: ≥ 2 options, one key of
                                             them · "Choose N": N set, > N options, exactly N existing
                                             keys · matching: bank ≥ 2, one key of it, not reused unless
                                             reusable · DIAGRAM_LABEL has its picture
                                             Fields like groups[1].questions[0].answer, messages name "Q7"
  repository/ExerciseRepository             findBySlug (whole aggregate), existsBySlug,
                                             nextExerciseId / nextGroupId / nextQuestionId,
                                             save → new stored version (0 new, +1 per save), delete
```

### 8.2 Ids of exercises, groups and questions

`attempt_answer` points at `question.id`, so a question must keep its id
across edits; matching children by position would attach old attempts to the
wrong question after a reorder. So `Exercise`, `QuestionGroup` and `Question`
carry ids, and new ones get theirs **before** they enter the aggregate:
`ExerciseRepository.next…Id()` takes the next value of the table's sequence
(the "repository provides the next identity" pattern). The mapper then
matches children by id.

**Database:** migration `V5` (database task 6) switched `exercise`,
`question_group` and `question` to `generated by default as identity` (same
sequences), so chosen ids can be inserted. The admin editor must send the group
and question ids it received (new items without id).

### 8.3 Application

| Port | Adapter |
|------|---------|
| `ExerciseRepository` (domain) | `JpaExerciseRepository` |
| `HtmlSanitizer` (application): `passage(html)`, `text(html)` → `SanitizedHtml` | `JsoupHtmlSanitizer` |
| `MarkupInspector` (application): `inspect(SanitizedHtml)` → `MarkupKeys` | `JsoupMarkupInspector` |
| `QuestionTypes` (application): code → family (enabled types) | `JdbcQuestionTypes` |
| `ReferenceData` (application): stage / topic / media exists? | `JdbcReferenceData` |
| `AttemptsLookup` (application): `hasAttempts`, `answeredQuestions` | `JdbcAttemptsLookup` (SQL on Practice's `attempt*` tables) |
| `ExerciseSummaries` (application): the admin list | `JdbcExerciseSummaries` |

The seven use cases are the methods of one application service,
`ExerciseAdmin` (they share loading, the version check and saving); each runs
in one transaction:

| Use case | Flow |
|----------|------|
| `list()` | `ExerciseSummaries` (SQL read model), newest change first |
| `get(slug)` | load (404, also for a malformed slug) → `AdminExerciseView` |
| `create(input)` | slug valid and free (else 422 `id`) → skill (default READING), target language (default `en`) → `ExerciseContentBuilder` → `Exercise.draft` → save (version 0) |
| `update(slug, input)` | load → `version` required (400) and equal to the stored one (409) → builder (existing ids allowed) → refuse removing questions that attempts point at (422 `questionGroups`, "Q6 can't be removed: learners have answered it…") → `editContent(content, policy)` → save (version + 1) |
| `publish(slug)` / `unpublish(slug)` | load → `publish(policy, now)` / `unpublish()` → save |
| `delete(slug)` | load → attempts? `archive()` + save : delete; 204 either way |

`ExerciseContentBuilder` turns the input into `ExerciseContent` and reports
every problem at once (422) with the JSON path of its field: sanitizes the
passage (then reads its keys), instructions, translations and explanations;
checks format, level, timer, stage + position, topics, media, question types,
languages (stored spelling kept), option keys, evidence ids, answers; gives
new groups and questions ids from the repository; an id that isn't one of
this exercise's (or is listed twice) is refused, so a save can't touch
another exercise's rows. The aggregate then checks its own rules.

### 8.4 Admin API (`content.web`, `/api/v1/admin/passages`, ADMIN or AUTHOR)

The server's contract (it supersedes `admin-panel-ui` design §4, whose
answer key was keyed by question number; numbers change when an author
reorders, ids don't):

```
GET  /                → [{id, title, status, revision, stageTitle, position, questionCount, updatedAt}]
GET  /{slug}          → AdminExerciseView
POST /                → 201 AdminExerciseView          body: ExerciseInput (with "id" = the slug)
PUT  /{slug}          → 200 AdminExerciseView          body: ExerciseInput (with "version")
POST /{slug}/publish  → 200 AdminExerciseView (422 with every publish violation)
POST /{slug}/unpublish→ 200 AdminExerciseView
DELETE /{slug}        → 204 (archived instead when learners have attempts)

AdminExerciseView  id, version, status, revision, skill, targetLanguage, title, format, level,
                   durationSeconds, lowTimeThresholdSeconds, stageId, position, topicIds, publishedAt,
                   passageHtml, wordCount,
                   glosses {gN: {surface, lemma, meanings {lang: {text, source, reviewed}}}},
                   questionGroups [{id, type, heading, instruction, instructionTranslations {lang: html},
                     chooseCount, maxWords, numbersAllowed, optionsReusable, mediaId, options [{key, label}],
                     questions [{id, n, nTo, text | pre + post, evidenceId, options, answer [..],
                       explanations {lang: {text, source, reviewed}}}]}]
ExerciseInput      the same fields minus the read-only ones (n, nTo, heading, surface, status,
                   revision, publishedAt, wordCount); create also takes skill and targetLanguage;
                   group / question "id" left out (null) = new; source defaults to HUMAN,
                   reviewed to false
```

`answer` is always a list (TFNG `["FALSE"]`, gap alternatives, "Choose N"
keys). The editor sends back what it loaded, changed. Errors: 422
`errors[{field, message}]` with paths like
`questionGroups[1].questions[0].answer`, `glosses.g1.meanings.xx`, `id`.

**HTML** (jsoup, requirement 5): allowed tags `p span mark em strong b i br`;
everything else is removed with every other attribute, event handler, style
and URL. `script`/`style`/`iframe` lose their content too; other unknown tags
keep their text, and a removed text block (div, heading, list item, table
row…) leaves a `<br>` so words don't run together. Comments are dropped.
Letters stay as they are (UTF-8); `<`, `&` are escaped. Sanitizing twice
changes nothing.

- `passage`: `span` may keep `class` (only the values `w`, `ev`),
  `data-g` (only on `span.w`) and `id` (only on `span.ev`). Key values are
  kept as written so the domain can report invalid ones (requirement 5.4).
  `data-az` is gone.
- `text` (instructions, explanations): no attributes at all, so they can't
  carry ids that clash with the page's own.
- `inspect`: every `span.w[data-g]` (key + the word's text) and every
  `span.ev[id]`, in document order, as written (repeats included).

**Persistence** (`JpaExerciseRepository`): one plain JPA entity per table,
no JPA associations; the adapter loads the twelve tables and builds the
aggregate with `Exercise.restore`, and saves it whole in an explicit order,
because no unique key in the schema is deferrable and Hibernate would insert
before it deletes:

1. Lock and re-read the exercise row (`refresh … FOR UPDATE`: a row loaded
   earlier in the transaction would otherwise keep its old version). Its
   `version` must equal the one the exercise was loaded with, else
   `ConflictException` (409, "changed by someone else"); then `+1`. A new
   exercise is inserted with version 0. `version` is a plain column, not
   `@Version` (child-only changes wouldn't bump it).
2. Remove the rows nothing points at (topics, all translations, options,
   answers, explanations); they are inserted again at the end.
3. Evidence and glosses matched by key: new keys inserted, glosses updated,
   removed glosses deleted.
4. Groups and questions matched by id: existing ones move to temporary
   negative positions (unique positions), removed questions are deleted,
   groups then questions get final position / group / stem / evidence row,
   removed groups go last (a group delete cascades to the questions still
   in it).
5. Evidence no longer in the passage deleted; passage row written (removed
   when the exercise has none); replaceable rows inserted.

Rows of identity tables are filled before `persist` (Hibernate inserts them
at once). Glosses and evidence load in document order (position in the
HTML). Concurrent saves of one exercise run one after another; the second is
rejected. `delete` locks and checks the version too, then deletes the row
(the database cascades). Removing a question that attempts point at is
refused by the database (`attempt_answer` → `question` is RESTRICT) → generic
409; task 4 will check it first and answer with a clear message.

---

## 8b. Catalog context (phase 2, built)

Browsing and filtering published exercises (database requirement 7, design
§5.0). **Read-only:** nothing is changed and there are no business rules to
protect, so there is no domain layer and no aggregate — a query use case and
an SQL read model (CQRS-lite), like Practice's `LearnerPassages`. Approved by
the user.

| Piece | Notes |
|-------|-------|
| `BrowseExercises.browse(CatalogQuery)` | checks the filters (known skill / format / levels, type codes and topic slugs well-formed, `maxMinutes` ≥ 1, page ≥ 0, size 1–50) → 400 listing every problem; the learner from `CurrentUser` (optional) |
| `BrowseExercises.filters(lang)` | the choices worth offering: only skills, formats, levels, question types and topics that some PUBLISHED exercise has |
| `ExerciseCatalog` → `JdbcExerciseCatalog` | one filtered count + page query (values always bound), then small queries for the cards on the page; PUBLISHED only; never reads answers or explanations |

```
GET /api/v1/exercises?skill=&format=&level=…&type=…&topic=…&maxMinutes=&notDone=&page=&size=&lang=
  → {items: [{id, title, skill, format, level, durationSeconds, questionCount, types[], topics[{slug, name}],
              done, bestScore{score, total} | null}], page, size, totalItems}
GET /api/v1/exercises/filters?lang=  → {skills[], formats[], levels[], types[], topics[{slug, name}]}
```

- Filters combine with AND; several values of one filter with OR. A type
  matches an exercise with at least one group of that type; an exercise
  without a timer always fits `maxMinutes`.
- `questionCount` counts question numbers ("Choose TWO" = 2), as the page shows.
- Topic names in the help language (`?lang=` → token → `Accept-Language`),
  per item with fallback, else the slug.
- Signed in: `done`, `bestScore` (best score/total ratio) and `notDone`
  (hide attempted ones); anonymous: `done` false, `bestScore` null, `notDone`
  ignored.
- Order: newest published first, then title.
- Public (`CatalogAccessRules`); a valid token adds the learner's progress.

---

## 9. REST API summary

| Method | Path | Access | Success |
|--------|------|--------|---------|
| POST | `/api/v1/auth/register` | public | 201 |
| POST | `/api/v1/auth/login` | public | 200 + cookie |
| POST | `/api/v1/auth/refresh` | refresh cookie | 200 + new cookie |
| POST | `/api/v1/auth/logout` | refresh cookie | 204 |
| GET | `/api/v1/auth/me` | token | 200 |
| GET | `/api/v1/exercises?…filters` | public (token optional) | 200 |
| GET | `/api/v1/exercises/filters?lang=` | public | 200 |
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

Every error is `application/problem+json` (`ApiExceptionHandler`, global so
unknown URLs are covered too).

| Cause | Status |
|-------|--------|
| no / invalid / expired token (filter or `UnauthenticatedException`) | 401 + `WWW-Authenticate: Bearer` |
| token without the required role (filter or `ForbiddenException`) | 403 |
| not found / not published (`NotFoundException`), unknown URL | 404 (unknown `/api/**` paths are denied first: 401/403) |
| malformed request (bad JSON, wrong types, missing params), `InvalidRequestException` | 400; `InvalidRequestException` may add `errors[{field, message}]` (e.g. every problem of a submission) |
| `DomainException` (value objects, `PublishPolicy`) | 422 + `errors[{field, message}]` |
| stale version, duplicate email (`ConflictException`) | 409 |
| any database constraint violation | 409 "The request conflicts with existing data." (SQL logged, never returned) |
| anything unexpected | 500 generic message (logged, never returned) |

---

## 10. What the frontend will need (UI tasks, not in this spec)

**Done** — `.kiro/specs/frontend/` (foundation, reading, admin, final pass):
`auth.js` + sign-in pages + admin guard, the reading page on §7.4, the
admin editor on §8.4, both public fixtures deleted, browser-tested in
headless Chrome.

- `auth.js`: login form handling, access token kept in memory, silent
  `POST /api/v1/auth/refresh` on page load, `apiFetch()` adding the Bearer
  header and retrying once after a refresh on `401`, logout.
- Login and register pages (Thymeleaf shells in `ui`).
- Admin pages: redirect to login when the API answers `401`/`403`.
- Admin editor: switch from the fixture to the §8.4 API (ids, answers nested
  in questions, `data-g` with meanings per language, `version` on save, 409 →
  "reload", 422 → field errors).
- Reading page (`reading/app.js`): load from `GET /api/v1/passages/{slug}`
  instead of the fixture; gloss meanings from `glosses[el.dataset.g]`
  (not `data-az`); build "Mini passage N" from `position`; send "Choose N"
  answers as a list; use `nTo` for "Questions 7–8".

---

## 11. Testing strategy

| Layer | How | Spring? |
|-------|-----|---------|
| domain | plain JUnit: value objects, aggregates and their invariants, equality, domain services | no |
| application | real use cases + Testcontainers where behaviour depends on transactions (e.g. revocation committed despite 401, BCrypt outside transactions via a recording hasher, concurrency) | yes |
| infrastructure | Testcontainers PostgreSQL with Hibernate `validate`: adapter round-trips (whole aggregates), JWT claims, BCrypt, token generator | slice |
| web | MockMvc + Testcontainers per endpoint, incl. 401/403 per role and bad tokens | yes |
| architecture | ArchUnit (§12); every rule proven once with planted violations | no |

Concurrency tests run real threads against PostgreSQL (e.g. 8 simultaneous
refreshes of one token; a copied old token racing the real user's new one),
repeated to check they're stable. Grading uses the shared case file
`src/test/resources/grading-cases.json` (every family; the browser stub's
tests can reuse it).

## 12. ArchUnit rules

Layering (1–6):

1. `com.bandup.ui..` does not depend on `com.bandup.api..`.
2. `..api..domain..` depends only on `java..` and `..domain..` classes.
3. `..api..application..` depends only on `java..`, `..domain..`,
   `..application..`, `org.springframework.stereotype..`,
   `org.springframework.transaction..`.
4. `..api..web..` does not depend on `..infrastructure..`.
5. No context depends on another context (only `api.shared` may be used by all).
6. No JPA `@Entity` outside `..infrastructure..`.

Tactical design (7–13):

7. No class sits directly in a context's `domain` package; everything lives in a module.
8. Aggregate modules of one context use each other only through an `…Id`
   `@ValueObject` (shared-kernel topics exempt).
9. At most one repository per aggregate module, and it's an interface (port).
10. Every domain class declares its building block (`@AggregateRoot`,
    `@Entity`, `@ValueObject`, `@DomainService`); exempt: repository ports,
    exceptions, nested types, annotations.
11. Exactly one `@AggregateRoot` per aggregate module.
12. A repository works with its module's root, never with an inner `@Entity`.
13. Inside an aggregate module every class lives in the package of its
    building block: `aggregate/`, `entity/`, `valueobject/`, `repository/`,
    `service/`, `exception/`.

---

## 13. Decisions

1. **Gloss keys** (`g1`, `g2`, …) are allocated by the **admin editor** when a
   word is marked. The server only validates them (`g\d+`, unique within the
   passage) and never renumbers them.
2. **Email verification and password reset**: later, not in phase 1.
3. **Phase 2**: Catalog (browse/filter) first. **Media is postponed** until
   after the first production release (no Listening yet). Until then
   `DIAGRAM_LABEL` exercises can't be published, because `PublishPolicy`
   requires an image.
4. **Tactical layout**: aggregate module first, then building-block packages
   (chosen by the user). `RefreshToken` is immutable so the `Session` root stays
   the only way to change a session even across packages.
5. **`ConflictException`** (not `ConcurrentModificationException`, which already
   means something else in `java.util`) for stale saves and duplicates.
6. **The error handler is global**, so unknown URLs are Problem Details too.
7. **Register doesn't sign in**; the UI calls login next. `me`/login don't
   return the email (requirement 8.6). Blank languages default to English.
8. **Unknown `/api/**` paths are denied** (401/403), not 404, per §6.4.
9. **Practice and Content tactical models** (§7.1, §8.1) follow Identity's
   layout: one aggregate each (`attempt`, `exercise`), split by building
   block. Question numbering moves to the shared kernel. Approved by the
   user, together with migration V5 (§8.2).
10. **Editing a PUBLISHED exercise must still pass the `PublishPolicy`** (and
    raises the revision): otherwise a save could show learners a broken
    exercise, and Practice's grading refuses broken published data.
11. **Content's entities are immutable**; the `Exercise` root swaps them.
12. **Exercise versions are checked under a row lock**, not with JPA
    `@Version` (§8.3): child-only edits must count as a change, and the check
    must see saves that committed while waiting.
13. **Sanitizer modes**: passages keep only the reading aids (`span.w[data-g]`,
    `span.ev[id]`, classes limited to `w`/`ev`); instructions and explanations
    keep no attributes. Removed text blocks leave a `<br>`.
14. **One `ExerciseAdmin` service** holds the seven admin use cases; the
    admin JSON nests answers and explanations in their question and carries
    group / question ids (§8.4). Domain violation paths use `questionGroups[…]`,
    the JSON key.
15. **A question learners have answered can't be removed** (422 with its
    number); archive the exercise instead.
16. **Catalog has no domain layer**: browsing is a read-only query; an
    aggregate would have nothing to guard (§8b).

### Open — product decisions needed

- The admin editor must switch to the §8.4 contract (ids, nested answers,
  `data-g`, meanings per language); a UI task.
- Registration reveals whether an email exists (409), while login hides it.
- No rate limiting of login attempts (a lockout would need a new table).
- `refresh_token` rows are never cleaned up (one new row per page load).
- Two tabs refreshing at the same moment end the session (the session lock
  makes this deterministic). Recommended fix: `auth.js` lets one tab refresh
  and the others reuse its result. **Done** in frontend foundation task 2 (Web Locks), browser-tested.

### UI side — closed

- The admin editor uses `data-g` with meanings per language (frontend admin
  task 3); both public fixtures with answer keys are deleted (reading task 5,
  admin task 7; `GET` on them is 404, browser-tested).
