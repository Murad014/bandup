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

- [x] 0. Content — tactical model (for approval, no code)
  - Design §8.1 and §4: one aggregate module `exercise`, split by building
    block. Approved by the user, together with the id decision in design §8.2
    (migration `V5`, done as database task 6).

- [x] 1. Content — domain
  - `content.domain.exercise`:
    - `aggregate/` `Exercise` (`draft`, `editContent`, `publish`,
      `unpublish`, `archive`, `numbering`, `restore`)
    - `entity/` `Passage`, `Gloss`, `QuestionGroup`, `Question` (changed only
      through `Exercise`)
    - `valueobject/` `ExerciseId`, `GroupId`, `QuestionId`, `Slug`, `Skill`,
      `Format`, `Level`, `ExerciseStatus`, `Revision`, `Timer`, `Placement`,
      `StageId`, `TopicId`, `MediaId`, `SanitizedHtml`, `MarkupKeys`,
      `GlossKey`, `GlossMeaning`, `EvidenceKey`, `QuestionType`,
      `QuestionFamily`, `WordLimit`, `Option`, `OptionKey`, `Stem`,
      `AnswerKey`, `Explanation`, `TextSource`
    - `service/` `PublishPolicy`
    - `repository/` `ExerciseRepository`
  - Numbering uses the shared `QuestionNumbering` (Practice task 1). JDK only.
  - _Verify_: unit tests: value objects, `syncMarkup` (add/remove, blocked when
    evidence is in use; editor's gloss keys kept as given, invalid/duplicate
    keys rejected), revision bump only when PUBLISHED, ARCHIVED can't be
    edited, every publish rule of requirement 4.2, numbering with "Choose N",
    children keep their ids across edits; ArchUnit rules 7–13 green.
  - _Requirements: 1.7, 4.2, 4.5, 5.2, 5.3, 5.4_
  - _Done_:
    - `content.domain.exercise` as listed, plus `GlossNote` and
      `ExerciseContent` (one editor save) in `valueobject/`.
    - Entities are immutable; `Exercise` swaps them. `editContent` takes the
      `PublishPolicy`: a PUBLISHED exercise must stay publishable (decision
      §13.10). `publish` takes the time (`publishedAt`).
    - Draft-time rules and publish rules as in design §8.1; every failure
      reports all violations and leaves the exercise unchanged.
  - _Verified_: 29 new tests: `ExerciseTest` 13 (sync and notes, editor keys
    kept, draft may be incomplete, all key problems at once, missing
    evidence, type-specific options/limits, edit keeps ids and renumbers,
    removing used evidence rejected, published edit → revision 2 and
    policy, lifecycle incl. archived, failed publish unchanged, "Choose
    TWO" numbering 1, 2, 3–4, 5, restore), `PublishPolicyTest` 9 (every
    rule), `ContentValueObjectsTest` 7. Mutation check: removing the
    revision bump and the "each option once" rule each failed the right
    test. 317 tests green, twice.
    - Also fixed a test-order clash found on the way: Practice tests now
      create users with unique emails (AuthApiTest commits its users).

- [x] 2. Content — persistence adapter
  - JPA entities for the content tables, `ExerciseMapper` (children matched
    by id / key), `JpaExerciseRepository` with `next…Id()` from the tables'
    sequences (possible since migration `V5`); stale version →
    `ConflictException` (409).
  - _Verify_: Testcontainers: the seeded Wolves exercise maps to the domain and
    back unchanged; a new exercise with every option ownership kind
    round-trips; stale save rejected; Hibernate `validate` passes.
  - _Requirements: 4.4_
  - _Done_:
    - 12 plain JPA entities (one per table) + `JpaExerciseRepository`; no
      separate mapper class: loading and saving live in the adapter (design
      §8.3 has the save order).
    - `ExerciseRepository.save` now returns the new stored version.
    - Version check under a row lock instead of `@Version` (decision §13.12).
  - _Verified_: `ContentPersistenceTest` (10, Testcontainers, Hibernate
    `validate`): seeded Wolves loads fully (25 glosses in order with az and
    MACHINE en meanings, evidence, groups, gap stem, word limit,
    explanations; passes the publish policy); Wolves saved back is
    unchanged and keeps every question / group / gloss / evidence row id;
    a new exercise with TFNG, "Choose TWO" (own options), headings (group
    bank) and gap, with translations everywhere, round-trips; an edit that
    reorders groups, drops a group while moving its question to a new
    group, removes a gloss and an evidence span and adds another keeps the
    ids and stores exactly the new state; dropping the passage removes its
    rows; stale save and stale delete → `ConflictException`, nothing
    changed; delete removes every child row; new ids come from the
    sequences; **two real concurrent saves → exactly one conflict**
    (stable over 5 runs; it caught a bug: a locking find kept the stale
    in-memory version, fixed with refresh under the lock). 327 tests green.

- [x] 3. Content — HTML adapters
  - `JsoupHtmlSanitizer` (allow-list from `reading-practice-ui` design §9 with
    `data-g`), `JsoupMarkupInspector`.
  - _Verify_: unit tests: scripts, event handlers, styles, links, unknown
    tags/attributes stripped; `span.w[data-g]`, `span.ev[id]`, emphasis kept;
    keys extracted from the seeded passage.
  - _Requirements: 5.1, 5.2_
  - _Done_:
    - Ports `HtmlSanitizer` (`passage`, `text`) and `MarkupInspector` in
      `content.application.port`; jsoup adapters in
      `content.infrastructure.html`. New dependency `org.jsoup:jsoup:1.21.1`.
    - Two modes (decision §13.13). Removed text blocks leave a `<br>`: a
      first version glued "Wolves" + "return" together, caught by a test.
  - _Verified_: `JsoupHtmlAdaptersTest` (12, no Spring): script / style /
    iframe removed with content; `on*`, `style`, links, `javascript:`,
    images, svg/math removed; unknown tags and attributes removed with text
    kept and words apart; comments dropped; `data-az` gone; reading aids and
    emphasis kept exactly; classes limited to `w`/`ev`, keys only on their
    spans (no `id="stageLabel"`); invalid key values kept for the domain;
    text mode keeps no attributes; Azerbaijani letters kept, `<` `&`
    escaped, `&nbsp;` kept; sanitizing twice = once; null/empty → empty;
    inspector: keys in document order with their words, repeats kept,
    spans without keys ignored. `SeededPassageMarkupTest` (Testcontainers):
    the seeded Wolves passage passes through unchanged; its 25 gloss keys
    (with the stored surface words) and 5 evidence ids come out in
    document order. 340 tests green.

- [x] 4. Content — use cases + admin API
  - The seven use cases (design §8.3), `AttemptsLookup` adapter (also used
    to refuse removing a question that attempts point at, with a clear
    message instead of the database's generic 409),
    `AdminExerciseController` under `/api/v1/admin/passages`.
  - _Verify_: MockMvc + Testcontainers: CRUD round-trip, draft save with
    incomplete content, publish blocked with field errors (422), stale version
    (409), delete with attempts → archived, revision bump on published save,
    sanitized HTML stored, 401 without token, 403 for a LEARNER token.
  - _Requirements: 4, 5, 7_
  - _Done_:
    - `ExerciseAdmin` (the seven use cases as methods), `ExerciseContentBuilder`,
      `ExerciseInput`, `AdminExerciseView`, `ExerciseSummary`; ports
      `QuestionTypes`, `ReferenceData`, `AttemptsLookup`, `ExerciseSummaries`
      with SQL adapters; `AdminExerciseController`.
    - Admin JSON per design §8.4 (ids, nested answers); domain violation
      paths renamed `groups[…]` → `questionGroups[…]` to match it.
    - Removing an answered question → 422 (was a generic 409 from the DB).
  - _Verified_: `AdminApiTest` (14, MockMvc + Testcontainers): 401 / 403
    LEARNER / AUTHOR + ADMIN allowed; list; full object with answers,
    meanings, explanations, ids, numbers; create incomplete draft → update
    with answers and a new question (ids kept) → stale PUT 409 → delete →
    404; sanitized passage / instruction / explanation stored; publish
    blocked with field errors, still DRAFT and hidden; publish → learners
    see it and can be graded; unpublish hides it; published save → revision
    2, broken published save 422; delete with attempts → ARCHIVED, hidden,
    can't publish or edit; answered question can't be removed (Q6);
    unknown level / stage / topic / language / type / foreign question id /
    bad evidence id / bad option key → field errors; duplicate or bad slug
    → `id`; key rules of the passage reach the author; missing version →
    400; malformed JSON → 400 without internals. Mutation check: skipping
    the answered-question check and skipping passage sanitizing each failed
    the right test. 354 tests green, twice.

- [x] Cover images (backend requirement 10, design decision 18)
  - Domain: `Exercise.cover()` / `ExerciseContent.cover` (optional
    `MediaId`; old constructors kept). Application: `ImageLibrary`, ports
    `ImageStore`, `MediaLibrary`; `ExerciseContentBuilder` checks the id.
  - Web: `AdminMediaController` (`POST /api/v1/admin/media`),
    `MediaController` (`GET /media/{publicId}`); admin view has
    `coverMediaId` + `coverUrl`; catalog card and passage have `coverUrl`
    (passage also `topics`). Upload limit 3 MB (`spring.servlet.multipart`).
  - _Verified_: `CoverImageApiTest` (4): upload + serve with cache and
    nosniff headers; fake / GIF / empty / too large refused; anonymous 401,
    learner 403, unknown 404; cover shows in editor, catalog and passage;
    unknown cover id is a field error.
