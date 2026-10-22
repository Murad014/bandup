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

- [ ] 1. Practice — domain
  - `GradingRule`, `Score`, `Grader`, `GivenAnswer`, `GradableExercise`,
    `Attempt` (factory `grade`), `AttemptRepository` port.
  - Shared case file `src/test/resources/grading-cases.json`.
  - _Verify_: unit tests from the shared cases (every family, word limit with
    numbers, "Choose N" partial / too many), totals and percent.
  - _Requirements: 3.2, 3.3_

- [ ] 2. Practice — adapters
  - SQL `GradableExercises` and `LearnerPassages` (passage view never selects
    answers), JPA attempt entities + mapper + adapter.
  - _Verify_: Testcontainers: the seeded Wolves exercise loads as a
    `GradableExercise` and as a `PassageView` (no answer/explanation/evidence
    fields); an attempt round-trips with its JSON snapshots.
  - _Requirements: 2, 3.3_

- [ ] 3. Practice — use cases + learner API
  - `GetLearnerPassage`, `SubmitAttempt`; `PassageController`,
    `AttemptController`.
  - _Verify_: MockMvc + Testcontainers: full az payload, no leaked fields,
    fallback per item, draft/unknown → 404, perfect and mixed attempts,
    "Choose TWO" partial points, snapshot unchanged after a content edit, 400
    cases, anonymous attempt vs attempt stored for the token's user.
  - _Requirements: 2, 3, 6, 7_
