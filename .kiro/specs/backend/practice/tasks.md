# Tasks — Backend · Practice context

The learner side: passage view without answers, submitting attempts,
server-side grading. **[Backend]** only. **Nothing is started.** Each task is
done only when requested, one at a time, and ends green (`sh gradlew test`).

- **Depends on:** foundation tasks 1–3 in `../tasks.md` (architecture, shared
  kernel). Task 3's "attempt stored for the token's user" check also needs
  Identity (`../identity/tasks.md`).
- **References:** requirements and design sections refer to
  `../requirements.md` and `../design.md`.

---

- [x] 0. Practice — tactical model (for approval, no code)
  - Design §7.1 and §4: one aggregate module `attempt`, split by building
    block. Approved by the user.

- [x] 1. Practice — domain (+ shared numbering)
  - Shared kernel `shared.domain.numbering`: `QuestionNumbering`
    (@DomainService), `NumberRange` (@ValueObject).
  - `practice.domain.attempt`:
    - `aggregate/` `Attempt` (immutable; `grade`, `restore`; score, total,
      percent derived)
    - `valueobject/` `AttemptId`, `ExerciseId`, `QuestionId`, `LearnerId`,
      `ExerciseRevision`, `AnswerSheet`, `GivenAnswer`, `GradedAnswer`,
      `Score`, `GradableExercise`, `GradableQuestion`, `GradingRule`,
      `QuestionFamily`, `WordLimit`
    - `service/` `Grader`
    - `exception/` `InvalidSubmissionException`
    - `repository/` `AttemptRepository`
  - Shared case file `src/test/resources/grading-cases.json`.
  - _Verify_: unit tests from the shared cases (every family, word limit with
    numbers, "Choose N" partial / too many), numbering ("Choose TWO" → 7–8),
    unknown/repeated numbers rejected, unanswered = 0, totals and percent,
    equality by id; ArchUnit rules 7–13 green for the new module.
  - _Requirements: 1.7, 2.4, 3.2, 3.3, 3.6_
  - _Done_:
    - `shared.domain.numbering`: `QuestionNumbering`, `NumberRange`.
    - `practice.domain.attempt` as listed. `Attempt.grade` creates the id
      itself; any number of a "Choose N" range identifies the question; all
      problems of a bad submission are reported at once
      (`InvalidSubmissionException`, renamed from `InvalidAnswerSheetException`
      because it also covers negative time). `GivenAnswer` is stored exactly
      as given; only the `Grader` normalizes (trim, lower-case, collapse
      spaces). A key repeated in "Choose N" counts once.
    - `grading-cases.json`: 26 cases covering all five families.
  - _Verified_: 261 tests green (+47: GraderTest 27 incl. a check that the
    file covers every family, AttemptTest 10, GradingRuleTest 6,
    QuestionNumberingTest 4; + 2 V5 schema tests). Probe run: a Practice
    class importing Identity's `UserId` failed rule 5; a second
    `@AggregateRoot` in `attempt` failed rule 11; probes deleted.

- [x] 2. Practice — adapters
  - SQL `GradableExercises` and `LearnerPassages` (passage view never selects
    answers), JPA attempt entities + mapper + adapter.
  - _Verify_: Testcontainers: the seeded Wolves exercise loads as a
    `GradableExercise` and as a `PassageView` (no answer/explanation/evidence
    fields); an attempt round-trips with its JSON snapshots.
  - _Requirements: 2, 3.3_
  - _Done_:
    - `application/PassageView` (read model), ports `LearnerPassages`,
      `GradableExercises` in `application/port`.
    - `infrastructure/readmodel`: `JdbcLearnerPassages`,
      `JdbcGradableExercises` (SQL over Content's tables).
    - `infrastructure/persistence`: `AttemptEntity` + `AttemptAnswerRow`
      (jsonb), `AttemptJson`, `JpaAttemptRepository`.
    - The view returns `stageTitle` + `position` instead of a ready
      `stageLabel`, and group numbers instead of a `heading`: wording is the
      web layer's job (task 3). Media left out until Media exists.
  - _Verified_: `PracticeAdaptersTest` (14, Testcontainers, Hibernate
    `validate`): Wolves as `GradableExercise` (rules, answers, evidence,
    explanations); draft/unknown not gradable; "Choose TWO" graded with its
    two keys; Wolves view in az (25 glosses, stage, timer, groups, gap
    stems); per-item fallback (tr → en; one az gloss missing → only that one
    in en); gloss missing everywhere left out; "Choose TWO" = Q7–8 with its
    own options and a heading bank with translated instruction; GRAMMAR /
    DRAFT / unknown → empty; no query or view field mentions answers,
    explanations or evidence (mutation-checked: a planted `question_answer`
    reference fails it); attempt round-trip with real jsonb (`string` /
    `array`) and DB score/`is_correct`; anonymous attempt; snapshot unchanged
    after an answer edit; saved only once; unknown learner refused. 275 tests
    green.

- [x] 3. Practice — use cases + learner API
  - `GetLearnerPassage`, `SubmitAttempt`; `PassageController`,
    `AttemptController`.
  - _Verify_: MockMvc + Testcontainers: full az payload, no leaked fields,
    fallback per item, draft/unknown → 404, perfect and mixed attempts,
    "Choose TWO" partial points, snapshot unchanged after a content edit, 400
    cases, anonymous attempt vs attempt stored for the token's user.
  - _Requirements: 2, 3, 6, 7_
  - _Done_:
    - `application`: `GetLearnerPassage`, `SubmitAttempt` (+ `Command`,
      `Answer`), `AttemptResult`.
    - `web`: `PassageController` + `PassageResponse`, `AttemptController` +
      `AttemptResponse` (design §7.4).
    - Shared: `InvalidRequestException` can carry `errors[]`, returned with
      the 400 (`ApiProblems.badRequest`).
    - A bad submission reports shape problems and exercise problems together.
    - `JpaAttemptRepository.save` now flushes (database rules fail inside
      the use case; same-transaction SQL sees the attempt).
    - `stageLabel` = stage title; `position` sent apart for the UI's wording.
  - _Verified_: `PracticeApiTest` (13, MockMvc + Testcontainers, each test
    rolled back): full az payload; no answer, explanation or evidence link
    outside the passage text; language from `?lang=` → token → browser,
    regional code → base, per-item fallback, none → en; draft / unknown →
    404 (GET and POST); a forged token → 401 on the public endpoint; perfect
    attempt 6/6 with explanation and evidence; mixed attempt (blank, wrong,
    spaces/case) 1/6 = 17%; "Choose TWO" Q7–8 with partial point, 3 letters
    → 0, answered via number 8; explanations in az with fallback to en and
    `support_language` stored; stored attempt keeps revision 1 and its
    snapshot after an answer edit while a new attempt grades against
    revision 2; token → attempt linked to that learner; every 400 problem
    listed at once (repeated, missing number, 0, unknown, negative time,
    both numbers of one "Choose TWO", missing time / passage, non-text
    value, bad JSON) and nothing stored; a client-sent score is ignored.
    288 tests green.

- [x] Learner history — requirement 11, design §7.5
  - [x] `LearnerHistory`, port `AttemptHistory`, `JdbcAttemptHistory`,
    `ProgressController` (`/api/v1/me/progress`, `/api/v1/me/attempts`,
    `/api/v1/me/attempts/{id}`), access rule `/api/v1/me/**` authenticated.
  - [x] Page `/progress` (`ProgressPageController`, `progress.html/css/js`):
    summary tiles, attempts newest first (cover, date, time, score
    coloured by percent), passage filter, pages of 10, Review / Try again,
    "not available any more" for unpublished passages, empty state; navbar
    "My progress" for signed-in users; reading page review mode
    (`?review=`: answers filled in, graded, no timer; "Start again" drops
    `?review`).
  - _Verified_: `ProgressApiTest` (4): own attempts only (anonymous and
    other learners excluded), totals (average 75 %, best 100 %, 100 s),
    newest first, paging, filter, 400 for bad page/size, review content with
    help-language explanations, stored grade unchanged after a content
    edit, `updatedSince`, unpublished → `available: false`, 404 for others'
    / anonymous / unknown / malformed ids, 401 without a token. Mutation:
    dropping the ownership condition fails all four.
    `ProgressBrowserTest` (3): practise → navbar link → page → Review
    (answers, grades, no timer) → Start again; signed out → sign in with
    `next=/progress`; empty state. 439 tests and 13 Selenium scenarios green;
    screenshots checked.
