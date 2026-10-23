# Tasks — Backend

All tasks are **[Backend]** only. **Phase 1 backend is complete** (foundation,
Identity, Practice, Content). Each task is done
only when requested, one at a time, and ends green (`sh gradlew test`).
Frontend work (login pages, `auth.js`, switching the UIs from their stubs to
these APIs) is not in these lists; see "Follow-ups" at the end.

This file holds the **foundation** (architecture + shared kernel) and the
**build order**. Each bounded context has its own task list, numbered from 1.

## Build order

| Step | Tasks | File |
|------|-------|------|
| 1 | Foundation 1–3: architecture, shared kernel | this file |
| 2 | Identity 1–3: users, login, JWT | [`identity/tasks.md`](identity/tasks.md) |
| 3 | Practice 1–3: learner passage, attempts, grading | [`practice/tasks.md`](practice/tasks.md) |
| 4 | Content 1–4: authoring, publish rules, sanitizer, admin API | [`content/tasks.md`](content/tasks.md) |
| 5 | Catalog 1 (phase 2): browse and filter + catalog page | [`catalog/tasks.md`](catalog/tasks.md) |

Practice and Content depend only on the foundation for their domain and
adapter tasks; their API tasks also need Identity (token user, role checks).

---

## Foundation — Architecture

- [x] 1. Two sides + skeleton + ArchUnit rules
  - `src/main/java` was cleaned to only `BandupApplication`; the old page
    controllers and the dev admin guard were removed. The UI controllers are
    rebuilt from zero in `com.bandup.ui` (templates/JS/CSS are kept).
  - Created `com.bandup.ui` and `com.bandup.api.{shared, identity, practice,
    content}.{domain, application, infrastructure, web}`, each with a
    `package-info.java` stating what belongs in that layer.
  - Added `com.tngtech.archunit:archunit-junit5:1.4.1` (test) and
    `architecture/ArchitectureTest` with the 6 rules from design §12. Rules
    allow empty packages while features are still being built.
  - _Verified_: 47 tests green (41 database + 6 architecture). Probe run: one
    planted violation per rule (ui → api, Spring in domain, JDBC in a use case,
    web → infrastructure, practice → content, `@Entity` in domain) made all 6
    rules fail on exactly those classes, while legit probes (pure domain record
    with int[]/enum/Optional, `@Service` + `@Transactional` use case, shared
    kernel used from Practice) were not flagged. Probes deleted afterwards.
  - _Requirements: 1_

## Foundation — Shared kernel

- [x] 2. Shared kernel — domain
  - In `com.bandup.api.shared.domain`, JDK only: `LanguageCode` (BCP-47 shape,
    case-insensitive equality that keeps the stored spelling, `base()`),
    `Translations<T>` (immutable, one value per language), `LanguageChain`
    (non-empty, no repeats, always ends with English; `pick`, `pickAll`),
    `LanguageFallbacks` (builds chains; bad user input never throws),
    `Violation`, `DomainException` (carries every violation).
  - _Verified_: 54 unit tests, no Spring: exact hit, fallback, missing item,
    `pt-BR → pt → en`, `kk → ru → en`, case-insensitive codes with stored
    spelling, unknown region → base, unknown/malformed/blank → English,
    English stops the walk, data without English still ends in English, loop
    in data, the guide's Emre example; value-object rules. ArchUnit rule 2
    now checks these 6 classes. 101 tests green.
  - _Requirements: 6_

- [x] 3. Shared kernel — language adapter, request language, errors
  - application: `LanguageCatalog` port; outcome exceptions
    `InvalidRequestException` (400), `UnauthenticatedException` (401),
    `ForbiddenException` (403), `NotFoundException` (404),
    `ConflictException` (409). Note: design §8.3 names
    `ConcurrentModificationException` for stale saves; `ConflictException` is
    used instead because that name already means something else in `java.util`.
  - infrastructure: `JdbcLanguageCatalog` (reads all languages, enabled or not).
  - web: `RequestLanguage.pick(explicit, userLanguage, Accept-Language)`
    (user language is empty until Identity provides it); `ApiProblems`
    factory (reused by Identity's security handlers for filter-level 401/403);
    global `ApiExceptionHandler` (global so unknown URLs are Problem Details
    too): `DomainException` → 422 with `errors[]`, DB constraint → 409 without
    SQL, anything unexpected → 500 without internal details.
  - _Verified_: `JdbcLanguageCatalogTest` (Testcontainers, seeded languages:
    `kk → ru → en`, disabled `tr` still resolves), `RequestLanguageTest`
    (priority order, q-weights, wildcard, malformed header),
    `ApiExceptionHandlerTest` (400/401/403/404/405/409/422/500, Spring MVC
    errors, `application/problem+json`, no SQL or exception text leaked).
    Live app: unknown `/api/v1/...` → Problem Details 404. 116 tests green.
  - _Requirements: 6, 7_

---

## Follow-ups outside these lists (need separate approval)

- **UI:** done — [`../frontend/tasks.md`](../frontend/tasks.md)
  (foundation: page shells, `auth.js`, sign-in, admin guard),
  [`../frontend/reading/tasks.md`](../frontend/reading/tasks.md) and
  [`../frontend/admin/tasks.md`](../frontend/admin/tasks.md).
- **Phase 2:** ~~Catalog (browse/filter)~~ done (`catalog/tasks.md`); Google sign-in, email verification /
  password reset.
- **After first production:** Media (images/audio; diagram questions, Listening).

## Open product decisions (Identity; see design §13)

- Registration reveals whether an email exists (409) while login hides it.
- No rate limiting of login attempts (a lockout needs a new table).
- `refresh_token` rows are never cleaned up.
- ~~Two tabs refreshing at once end the session~~: fixed in `auth.js`
  (frontend foundation task 2: single-flight refresh across tabs).
