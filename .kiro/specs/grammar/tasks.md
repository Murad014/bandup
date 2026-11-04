# Tasks — Grammar

Each task ends green (`sh gradlew test`; browser tasks also `uiTest`) and
updates `requirements.md` / `design.md` / this file in the same step. Bug
fixes: failing test first. No code before the user approves this spec.

---

- [x] 0. Spec approved by the user (requirements, design, lesson format)

- [x] 1. Domain: `lesson` module
  - `GrammarLesson`, item types, `LessonPolicy`, `AnswerNormaliser`, `TileShuffler`,
    `GrammarLessonRepository` (port).
  - _Verify_: `LessonPolicyTest`, `ItemTest`, `GrammarLessonTest`,
    `AnswerNormaliserTest`, `TileShufflerTest` (every format rule with its JSON
    path, all at once; grading of each type; normalising; shuffling); ArchUnit
    green; mutation checks (shuffle order, duplicate violation, revision raise,
    extra tiles) all caught.
  - _Requirements: 5, 9.2, 9.5, 11.1_

- [x] 2. Domain: `run`, `progress`, `review` modules
  - `LessonRun`, `GrammarProgress`, `CourseUnlocking`, `ReviewCard`, `Box`,
    and the three repository ports.
  - _Verify_: `LessonRunTest`, `GrammarProgressTest`, `CourseUnlockingTest`,
    `ReviewCardTest` (mastery and retries, finish once, daily limit per level,
    replay, guest, inserted lesson, boxes and "once per day"); ArchUnit green;
    mutation checks caught (one survivor led to a simpler rule: every wrong
    check answer is a retry).
  - _Requirements: 3, 4.5, 6.4, 6.5, 8.2_

- [x] 3. Database `V9__grammar.sql` + persistence adapters
  - Tables of design §7; JPA entities, `LessonJson`, `Learners`, the four
    repository adapters. Database spec updated (`database/` task 10, design
    §6b, `schema.dbml`).
  - _Verify_: `GrammarPersistenceTest` (Hibernate validate; every aggregate
    round-trips incl. a second language and optional parts; version → 409;
    unique position; one card per item; used lesson can't be deleted; cleanup
    only old unfinished runs); `SchemaConstraintsTest` counts 35 tables;
    mutation checks all caught.
  - _Requirements: 8, 9.4_

- [x] 4. Admin use cases + admin API
  - `LessonAdmin`, `LessonInputBuilder`, `LessonViews`, `JsoupTextSanitizer`,
    `JdbcLessonSummaries`, `JdbcLessonUsage`, `AdminGrammarLessonController`
    (the shared admin rule covers access; `ReviewCardRepository.existsForLesson`
    replaced by the `LessonUsage` port).
  - _Verify_: `AdminGrammarApiTest` (test data `src/test/resources/grammar/b1-lesson.json`):
    roles; import with string or language texts; every format problem at once
    with JSON paths; unreadable shapes first; id rules and 409; sanitizing;
    version 400/409; published edit raises the revision; removed check items
    drop their cards; publish at the end / at a position / position taken 409;
    unpublish; delete vs archive; archived can't be edited; list filters; 404.
    Mutation checks caught (the duplicate-id check now asserts its message).
  - _Requirements: 9_

- [x] 5. Learner use cases + learner API
  - `GrammarCourse`, `LessonRuns`, `GrammarReview`, `RunCleanup`, `LessonAccess`,
    `Learner`, `LearnerItems`, `LearnerViews`; `JdbcLearnerCourses`,
    `ZonedGrammarDays`, `RunCleanupSchedule`, `GrammarAccessRules`; two
    controllers; `bandup.grammar.*` settings in `application.yaml`.
  - _Verify_: `GrammarLearnerApiTest` (19: levels; chosen level; guest course
    and lesson 1 only; paging and topic filter; language; no keys in lessons,
    runs and due cards; tiles shuffled per run; a full guest run graded on the
    server, retries, why, 409 after finishing; wrong answer kinds and unknown
    items 422; runs only their learner's; lesson edited mid-run 409; finishing
    saves progress, fills the deck and locks the next until tomorrow (403
    codes); replays free and without duplicate cards; guests save nothing;
    review boxes and once-a-day; review needs a token and own cards; cards of
    hidden lessons not shown; draft/unknown lessons 404; cleanup) and
    `GrammarKeysTest`. Mutation checks caught (a survivor added the per-run
    shuffle test).
  - _Requirements: 1.2, 2, 3, 4, 6, 7, 8_

- [x] 6. Seed lessons `V10__grammar_seed_lessons.sql`
  - `a2-articles-1`, `b1-present-perfect-past-simple-1`,
    `b2-second-third-conditional-1` (from the prototype, same texts and
    items) and a new `c1-inversion-1` (inversion after negative adverbials:
    5 learn cards, worked example, 3 practice, 7 check items). Each is
    PUBLISHED, added only if its id is free, at the end of its level's course
    (position 1 on a fresh database). Bodies in the stored shape (English only).
  - _Verify_: `SeedLessonsTest` (one published lesson per level at position 1;
    each loads through the format rules; every item accepts its own right
    answer); full suite green; a seed with tiles in answer order fails the test.
  - _Requirements: scope, decision 2_

- [x] 7. UI: course page
  - `GrammarPageController` (`/grammar`), `grammar.html`, `js/grammar/api.js`,
    `js/grammar/course.js`, `css/grammar.css` (design §6.2 mapping); navbar
    Grammar enabled; the home page's Grammar card is a link.
  - _Verify_: `UiPagesTest` (route, navbar link without "soon", shell without
    lesson content); `GrammarBrowserTest` (home and navbar lead to /grammar;
    guest picks a level, sees the course with the seed lesson NEXT, level kept
    after reload, change level; signed-in level saved on the server with the
    review card; fits a 375 px phone; screenshots light/dark/phone in
    `build/screenshots/grammar-*.png`); `AccessibilityBrowserTest` covers
    `/grammar`; `CatalogBrowserTest` updated (Grammar card is a link).
  - _Requirements: 1, 2, 10_

- [x] 8. UI: lesson page
  - `GrammarPageController` `/grammar/lessons/{slug}`, `grammar-lesson.html`,
    `js/grammar/lesson.js`, `js/grammar/items.js` (4 types + feedback), lesson
    styles in `grammar.css`, navbar guard while a lesson runs.
  - _Verify_: `GrammarLessonBrowserTest` (guest does the whole A2 seed lesson:
    pills, learn cards, worked example, practice with why and correct answer,
    a wrong check item comes back at the end, mastery 5/7, done with 1 retry;
    the guard blocks the navbar during the lesson and not after; a learner's
    finished lesson is DONE in the course with "next review tomorrow"; phone
    width; unknown lesson message; screenshots `build/screenshots/grammar-lesson-*.png`);
    `UiPagesTest` (route; shell without lesson content). Mutation check: a
    wrong item not re-queued fails the test.
  - _Requirements: 4, 5, 10.4_

- [x] 9. UI: review page
  - `GrammarPageController` `/grammar/review`, `grammar-review.html`,
    `js/grammar/review.js` (reuses `items.js`), review styles in `grammar.css`.
  - _Verify_: `GrammarReviewBrowserTest` (a learner finishes the A2 lesson,
    the 7 cards are made due through `due_on`; the course says "7 items
    due" and Review opens the page; topic label; a wrong card says "Next
    review: tomorrow" and comes back last; right cards say "in 3 days"; the
    returning card stays "tomorrow"; done with 7 cards and 1 retry; 6 cards
    in box 2; the course then says "next review tomorrow"; nothing due →
    "Nothing to review"; guest → "Sign in to review"; screenshots
    `build/screenshots/grammar-review-*.png`); `UiPagesTest` (route, shell
    without card content); `AccessibilityBrowserTest` covers `/grammar/review`.
    Mutation check: a wrong card not re-queued fails the test.
  - _Requirements: 6_

- [x] 10. Final pass
  - `backend/design.md`: §4 packages (grammar context and
    `GrammarPageController`), §9 API rows, decision 22 linking this spec;
    `reading-practice-ui` navbar note (Grammar enabled).
  - _Verify_: full suite green (586 tests).
  - _Requirements: all_

- [x] 11. Admin panel: Grammar section
  - `AdminPageController` `/admin/grammar`, `/admin/grammar/new`,
    `/admin/grammar/{slug}/edit`; sidebar item and dashboard card;
    `admin/grammar.html`, `admin/grammar-edit.html`, `js/admin/grammar-api.js`,
    `grammar-page.js`, `grammar-edit.js` (preview through `rich()` of
    `js/grammar/items.js`); styles in `admin.css`.
  - Bug found on the way: a taken position answered the generic "The request
    conflicts with existing data." Now `LessonAdmin` checks
    `GrammarLessonRepository.positionTaken` on publish and on an edit of a
    published lesson: 409 "Position N is already used in the B1 course."
  - _Verify_: `AdminGrammarBrowserTest` (list with the 4 seed lessons,
    level filter, search, Open link; a new lesson: bad JSON told before
    sending, server errors with their paths, saved as a draft and the address
    changes, preview shows right options / accepted answers / `az` texts,
    state fields not in the JSON, the id can't change, a taken position
    shows the server's message, published at the end and in the learner
    course, unpublished and gone from it, deleted from the list; unsaved
    changes ask before leaving); `AdminGrammarApiTest` (taken position on
    publish and on edit — failing first; mutation: removing either check
    fails a test); `UiPagesTest` (routes, sidebar, dashboard card, no data);
    `AccessibilityBrowserTest` (both pages). Screenshots
    `build/screenshots/admin-grammar-*.png`.
  - _Requirements: 9.4, 12_

- [x] 12. n8n grammar generator
  - `ai-docs/n8n/Orbiko - Grammar Generator.json` (25 nodes; built from the
    reading generator's pattern): form → brief → Claude writes → code
    validators (retry ×2) → GPT blind solve → compare → Claude fixes flagged
    items (×2) → login → `POST /api/v1/admin/grammar/lessons` (DRAFT) →
    Done / Orbiko's errors. Placeholders only for URL, email and password.
  - _Verify_: every code step run with JavaScript for Automation (`osascript`):
    the brief (id, derived topic key, bad level / rule / number); the
    validators accept a good lesson (also in ```json fences) and catch 24
    broken ones, each with its path; the solve sheet has no keys; compare
    accepts case/punctuation variants and flags a wrong answer, a second
    correct answer, a note and a skipped item; retry/fix stop after 2; the
    body has no `position`; error messages for 422/409. The body was sent to
    the local app (201 DRAFT, again 409, then deleted). The file imports into
    a throwaway n8n (25 nodes, all links).
  - _Requirements: 13_

- [x] Bug: "Next review" text counted from the browser's clock
  - Found when the GitHub build ran at 20:17 UTC (already the next day in
    Baku): 3 browser tests saw "in 2 days" / "on Thursday" instead of
    "tomorrow". Learners outside Baku's zone saw the same.
  - Failing test first: `HeadlessChrome` runs Chrome in a zone whose date is
    not Baku's (`awayFromBaku`), so the 3 tests failed at any hour.
  - Fix: the API sends `nextInDays` (course review, review answers,
    vocabulary schedule) and the vocabulary page's `today`; the pages only
    turn numbers into words (design decision 12; vocabulary decision 13).
    API tests check the new fields; a browser check of "added today" catches
    the old code (mutation).

## Later (not v1)

- Azerbaijani help on screen; streaks / XP; grammar on "My progress".
