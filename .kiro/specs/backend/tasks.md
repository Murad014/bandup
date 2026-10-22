# Tasks — Backend

All tasks are **[Backend]** only. **Nothing is started.** Each task is done
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

- [ ] 2. Shared kernel — domain
  - `LanguageCode`, `Translations<T>`, `LanguageChain`, `LanguageFallbacks`,
    `DomainException`, `Violation`. JDK only.
  - _Verify_: unit tests (no Spring): exact hit, fallback, missing item,
    `pt-BR → pt → en`, `kk → ru → en`, case-insensitive codes, unknown
    region/language, loop in data.
  - _Requirements: 6_

- [ ] 3. Shared kernel — language adapter, request language, errors
  - `LanguageCatalog` + `JdbcLanguageCatalog`; `RequestLanguage`;
    `ProblemDetail` handler (incl. `errors` for 422, 401/403 bodies).
  - _Verify_: adapter test on seeded languages (Testcontainers); web tests for
    each status mapping.
  - _Requirements: 6, 7_

---

## Follow-ups outside these lists (need separate approval)

- **UI:** `auth.js`, login/register pages, navbar Admin link from the token.
- **UI:** reading page → passage + attempt APIs; remove the public reading fixture.
- **UI:** admin editor → admin API (`data-g`, meanings per language); remove the
  public admin fixture.
- **Phase 2:** Catalog (browse/filter), Google sign-in, email verification /
  password reset.
- **After first production:** Media (images/audio; diagram questions, Listening).
