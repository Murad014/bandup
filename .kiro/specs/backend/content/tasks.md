# Tasks — Backend · Content context

The admin side: authoring exercises, publish rules, HTML sanitizing, the admin
API. **[Backend]** only. **Nothing is started.** Each task is done only when
requested, one at a time, and ends green (`sh gradlew test`).

- **Depends on:** foundation tasks 1–3 in `../tasks.md` (architecture, shared
  kernel). Task 4 (admin API, role checks) also needs Identity
  (`../identity/tasks.md`).
- **References:** requirements and design sections refer to
  `../requirements.md` and `../design.md`.

---

- [ ] 1. Content — domain
  - `Exercise` aggregate and value objects, `QuestionNumbering`,
    `PublishPolicy`, `ExerciseRepository` port. JDK only.
  - _Verify_: unit tests: value objects, `syncMarkup` (add/remove, blocked when
    evidence is in use; editor's gloss keys kept as given, invalid/duplicate
    keys rejected), revision bump only when PUBLISHED, every publish rule of
    requirement 4.2, numbering with "Choose N".
  - _Requirements: 4.2, 4.5, 5.2, 5.3, 5.4_

- [ ] 2. Content — persistence adapter
  - JPA entities for the content tables, `ExerciseMapper`,
    `JpaExerciseRepository`; stale version → `ConcurrentModificationException`.
  - _Verify_: Testcontainers: the seeded Wolves exercise maps to the domain and
    back unchanged; a new exercise with every option ownership kind
    round-trips; stale save rejected; Hibernate `validate` passes.
  - _Requirements: 4.4_

- [ ] 3. Content — HTML adapters
  - `JsoupHtmlSanitizer` (allow-list from `reading-practice-ui` design §9 with
    `data-g`), `JsoupMarkupInspector`.
  - _Verify_: unit tests: scripts, event handlers, styles, links, unknown
    tags/attributes stripped; `span.w[data-g]`, `span.ev[id]`, emphasis kept;
    keys extracted from the seeded passage.
  - _Requirements: 5.1, 5.2_

- [ ] 4. Content — use cases + admin API
  - The seven use cases (design §8.3), `AttemptsLookup` adapter,
    `AdminExerciseController` under `/api/v1/admin/passages`.
  - _Verify_: MockMvc + Testcontainers: CRUD round-trip, draft save with
    incomplete content, publish blocked with field errors (422), stale version
    (409), delete with attempts → archived, revision bump on published save,
    sanitized HTML stored, 401 without token, 403 for a LEARNER token.
  - _Requirements: 4, 5, 7_
