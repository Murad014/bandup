# Design — Grammar

## 1. Decisions at a glance

| Topic | Decision |
|-------|----------|
| Context | New backend context `com.bandup.api.grammar`; never imports Practice or Content classes (ArchUnit rule 5) |
| Content model | A lesson is a **document**: scalar columns for what is queried (slug, level, topic, position, status) + one JSONB `content` column for the body (`lesson-format.md`) |
| Course length | Unlimited: course = all PUBLISHED lessons of a level, ordered by `position` |
| Levels | `A2`, `B1`, `B2`, `C1` (own `GrammarLevel` value object) |
| Grading | On the server, one item per request; keys never in learner responses |
| Learner state | 3 aggregates: `LessonRun`, `GrammarProgress`, `ReviewCard` |
| Days | `LocalDate` in zone `bandup.grammar.day-zone` (default `Asia/Baku`), from the injected `Clock` |
| Guests | First lesson of each level only; runs without a learner; no progress, no review |
| Admin | JSON API (`/api/v1/admin/grammar/lessons`, n8n sends here) + the admin panel's Grammar section (§6.5) |
| UI | 3 Thymeleaf shells + ES modules, `app.css` tokens, shared navbar |

---

## 2. Packages

```
com.bandup.api.grammar
├── domain/
│   ├── lesson/
│   │   ├── aggregate/      GrammarLesson
│   │   ├── valueobject/    LessonId, LessonSlug, GrammarLevel, Topic, LessonStatus, Revision,
│   │   │                   Position, LessonText (Translations<String>), LearnCard (+ Example),
│   │   │                   WorkedExample, ItemId, Item (sealed: Choose, Type, Fix, Build),
│   │   │                   GivenAnswer (Choice | Text | Tiles), ItemFeedback,
│   │   │                   LessonBody (everything one save carries)
│   │   │                   (no entity/: items are value objects, the root is the only entity)
│   │   ├── service/        LessonPolicy (format rules), AnswerNormaliser, TileShuffler
│   │   └── repository/     GrammarLessonRepository
│   ├── run/
│   │   ├── aggregate/      LessonRun
│   │   ├── valueobject/    RunId, LessonRevision, RunOutcome
│   │   └── repository/     LessonRunRepository
│   ├── progress/
│   │   ├── aggregate/      GrammarProgress
│   │   ├── valueobject/    LearnerId, CourseLevel, FinishedLesson, LessonState (DONE | NEXT | LOCKED + reason)
│   │   ├── service/        CourseUnlocking
│   │   └── repository/     GrammarProgressRepository
│   └── review/
│       ├── aggregate/      ReviewCard
│       ├── valueobject/    ReviewCardId, Box, ReviewSchedule
│       └── repository/     ReviewCardRepository
├── application/            GrammarCourse, LessonRuns, GrammarReview, RunCleanup (learner use cases),
│   │                       LessonAccess, Learner, LearnerItems (helpers),
│   │                       LessonAdmin, LessonInputBuilder, LessonInput, AdminLessonView, LessonSummary,
│   │                       LessonViews (admin); view/LearnerViews (learner response records)
│   └── port/               LearnerCourses (read model), LessonSummaries, LessonUsage,
│                           TextSanitizer, GrammarDays (today in the zone)
├── infrastructure/
│   ├── persistence/        GrammarLessonEntity (content as jsonb), LessonRunEntity,
│   │                       GrammarProgressEntity + FinishedLessonRow, ReviewCardEntity,
│   │                       Jpa…Repository adapters, LessonJson, Learners
│   ├── readmodel/          JdbcLearnerCourses, JdbcLessonSummaries, JdbcLessonUsage
│   ├── html/               JsoupTextSanitizer (b, i, br only)
│   ├── config/             GrammarProperties (day-zone, guest run TTL), RunCleanupSchedule
│   └── security/           GrammarAccessRules
└── web/                    GrammarCourseController, GrammarReviewController,
                            AdminGrammarLessonController, request/response records

com.bandup.ui               GrammarPageController (/grammar, /grammar/lessons/{slug}, /grammar/review)
```

Lombok as in the other contexts; no `package-info.java`.

---

## 3. Domain (tactical model)

### 3.1 `lesson/` — GrammarLesson (aggregate root)

```
GrammarLesson   @AggregateRoot   equal by LessonId
  id: LessonId (bigint, from the repository's sequence)   slug: LessonSlug (unique)
  body: LessonBody (level, topic, title, goal, minutes, learn, worked, practice, check)
  position?: Position   status: DRAFT | PUBLISHED | ARCHIVED
  revision: Revision   version: long (optimistic lock, checked under a row lock like Exercise)

  draft(id, slug, LessonBody, LessonPolicy)          DRAFT, revision 1
  edit(LessonBody, LessonPolicy) → removed check-item ids
                                                     PUBLISHED → revision + 1; ARCHIVED → refused;
                                                     a position in the body moves the lesson
  publish(position, now)                             the caller passes the body's position or the end
                                                     of the course; publishedAt set once
  unpublish() → DRAFT     archive() → ARCHIVED (final)
  grade(ItemId, GivenAnswer, AnswerNormaliser, LanguageChain) → ItemFeedback(correct, expected, why?, explanation)
                                                     unknown item → violation "itemId"
  find(ItemId) · isCheckItem(ItemId) · checkItems()
  restore(..)                                        stored body must pass the policy (else IllegalArgumentException)
```

Repository `GrammarLessonRepository`: `findBySlug`, `findById`, `existsBySlug`,
`nextLessonId`, `positionTaken(level, position, except)`, `lastPublishedPosition(level)`, `save` (→ new version),
`delete`.

- **Item** (sealed value object): `Item.Choose(id, prompt, options, answer, whys)`,
  `Item.Type(id, prompt, sentence, accept, show)`, `Item.Fix(id, prompt, wrong,
  accept, show)`, `Item.Build(id, prompt, tiles, answer)`; each has
  `explanation` and `grade(GivenAnswer, AnswerNormaliser, LanguageChain)`. An
  answer of the wrong kind (text for `choose`, an index out of range, a blank
  text, tiles that aren't exactly the item's tiles) → violation `answer` (422).
- **LessonPolicy** (domain service): every rule of `lesson-format.md`, all
  violations at once with JSON paths (`check[3].answer`). Runs on every save,
  drafts included, because the pipeline needs the errors to retry.
- **AnswerNormaliser**: the normalising rules (lowercase, trim, spaces,
  trailing punctuation, `’` → `'`).
- **TileShuffler**: deterministic shuffle from a seed (the run id), never
  returning answer order (rotates until it isn't). The application uses it
  when it builds the learner's items, so the stored tiles stay as authored.

### 3.2 `run/` — LessonRun (aggregate root)

```
LessonRun   @AggregateRoot   equal by RunId (random UUID)
  lesson: LessonId   lessonRevision: LessonRevision   learner?: LearnerId (empty = guest)
  startedAt, finishedAt?: Instant   mastered: Set<ItemId>   retries: int   answered: int

  start(lesson, revision, learner?, now)
  record(ItemId, correct, isCheckItem, checkTotal, now) → RunOutcome(mastered, total, retries, finished, justFinished)
     practice item: counted only
     check item: correct → mastered (a set: twice counts once); wrong → retries + 1
     all check items mastered → finishedAt = now; a finished run refuses answers
                                (IllegalStateException; the use case answers 409 first)
  belongsTo(caller?)   a learner's run: only that learner; a guest run: whoever holds the id
```

`RunId.seed()` (from the UUID) seeds the tile shuffle, so reloading a run
shows the same order. Repository: `findById` (locks the row), `save`,
`deleteUnfinishedStartedBefore(cutoff)`.

Modules share only `…Id` types (ArchUnit rule 8), so `run` has its own
`LessonRevision` and `progress` its own `CourseLevel` (same codes as
`GrammarLevel`); the application maps between them. `LearnerId` lives in
`progress` and is used by `run` and `review`.

### 3.3 `progress/` — GrammarProgress (aggregate root, one per learner)

```
GrammarProgress   @AggregateRoot   equal by LearnerId
  level?: CourseLevel   finished: Map<LessonId, FinishedLesson(level, firstFinishedOn, timesFinished)>
  NEW_LESSONS_PER_DAY = 1

  start(learner) · chooseLevel(level)
  finish(lessonId, level, today) → true when it was a NEW lesson (a replay raises timesFinished)
  hasFinished(lessonId) · newLessonsOn(level, day) · mayStartNewLesson(level, today)
```

**CourseUnlocking** (domain service). The course is unlimited, so it gets
only the shown lessons (one page) plus two facts about the whole course,
which the read model finds: the course's first lesson and the learner's
first unfinished one.

```
stateOf(lesson, progress?, level, firstUnfinished?, firstOfCourse?, today) → LessonState
states(shown lessons, …) → one LessonState each, in order
  guest:    firstOfCourse → NEXT, others → LOCKED SIGN_IN
  learner:  finished → DONE (always replayable)
            firstUnfinished → NEXT, or LOCKED TOMORROW when today's new lesson is done
            others → LOCKED FINISH_PREVIOUS
```

A lesson inserted in the middle of a course becomes the NEXT one of learners
who passed that position; finished lessons are kept by id. Repository:
`findByLearner` (locks), `save`.

### 3.4 `review/` — ReviewCard (aggregate root)

```
ReviewCard   @AggregateRoot   equal by ReviewCardId (random UUID)
  learner: LearnerId   lesson: LessonId   item: ItemId
  box: Box (1–4)   dueOn: LocalDate   lastAnsweredOn?: LocalDate

  create(learner, lesson, item, today)     box 1, due tomorrow
  answer(correct, today) → ReviewSchedule(box, dueOn, changed)
     first answer today: correct → box + 1 (4 stays 4), wrong → box 1; dueOn = today + interval(box)
     already answered today: no change (the session repeats it until correct)
  isDue(today) (overdue counts) · belongsTo(learner)
  Box intervals: 1 → +1 day, 2 → +3, 3 → +7, 4 → +14
```

One card per (learner, lesson, item). A card is its own aggregate (a deck can
hold thousands of cards; loading all of them to answer one would be waste).
Repository: `findById` (locks), `itemsInDeck(learner, lesson)`, `save`,
`saveAll`, `deleteForItems(lesson, items)`.

---

## 4. Application

### 4.1 Learner use cases — built

Split by job into three services plus two helpers:

| Service | Use case | Flow |
|---------|----------|------|
| `GrammarCourse` | `levels()` | read model: published lessons per level (all 4 levels, 0 when none) |
| | `course(level, topic?, page, size, lang)` | `LearnerCourses.facts` (available, finished, first lesson, first unfinished) + one page + topics → `CourseUnlocking.states` → entries, `next`, review summary (signed in only) |
| | `me()` / `chooseLevel(level)` | token required; load or start the progress → `chooseLevel` → save |
| | `lesson(slug, lang)` | `LessonAccess.open` → lesson view without items (worked example with its answer, counts of items) |
| `LessonRuns` | `start(slug, lang)` | `LessonAccess.open` → `LessonRun.start` (lesson revision) → save → items without keys, tiles shuffled by the run id |
| | `answer(runId, itemId, answer, lang)` | load the run (404 unless `belongsTo` the caller) → finished → 409 → lesson must be published at the run's revision (else 409 "updated, start again") → `grade` → `run.record` → save → when it just finished and the run has a learner: `progress.finish` + a `ReviewCard` per check item not yet in the deck, same transaction |
| `GrammarReview` | `due(lang)` | token required; up to 20 due cards of PUBLISHED lessons, oldest due first → items without keys + `dueTotal` |
| | `answer(cardId, answer, lang)` | load the card (404 unless the caller's) → lesson published (else 404) → `grade` → `card.answer(correct, today)` → save |
| `RunCleanup` | `purge()` | deletes unfinished runs started more than 7 days ago (nightly, `RunCleanupSchedule`) |

Helpers: `LessonAccess` (published lesson by slug, else 404; `CourseUnlocking`
with the caller's progress; guest locked → 401, learner locked → 403 with
`code` TOMORROW or FINISH_PREVIOUS) and `Learner` (caller → `LearnerId`,
language chain, level parsing → 422 `level`). `LearnerItems` turns items
into key-free views and reads the JSON answer: a number (choose), a string
(type, fix) or a list of strings (build); anything else → 422 `answer`.

### 4.2 `LessonAdmin` (admin use cases, one service like `ExerciseAdmin`) — built

| Use case | Flow |
|----------|------|
| `list(level?, status?)` | `LessonSummaries` read model, newest change first (`checkCount` from the JSON) |
| `get(slug)` | load (404, also for a malformed slug) → `AdminLessonView` (full body, with keys, texts as language objects) |
| `create(input)` | slug valid (422 `id`) and free (409 "already exists") → `LessonInputBuilder` → `GrammarLesson.draft` (policy) → save |
| `update(slug, input)` | load → `version` required (400) and equal (409) → builder → `edit` (policy; ARCHIVED → 422 `status`) → drop review cards of removed check items → save |
| `publish(slug, position?)` | load → the given position, else the lesson's own, else `last published of the level + 1` (or 1) → `publish` → save; a taken position → 409 (unique index) |
| `unpublish(slug)` | load → `unpublish` → save |
| `delete(slug)` | `LessonUsage.isUsed` (runs, finished lists or cards)? `archive()` : delete; 204 either way |

**`LessonInputBuilder`**: reads every text as a string (English) or an
object of languages (known languages only, stored spelling), sanitizes it,
and turns a missing value into an empty one, so the policy reports all of
them in one go. Shapes it can't read — unknown level or item type, a text
that is neither string nor object, a non-number `choose` answer, a
non-string `build` answer, an example without `ok` — are reported first,
all together (422); the policy runs once they are fixed. Plain values
(options, accept, tiles, answers) are trimmed, not sanitized: the UI renders
them as text.

### 4.3 Ports

| Port | Adapter |
|------|---------|
| `GrammarLessonRepository`, `LessonRunRepository`, `GrammarProgressRepository`, `ReviewCardRepository` (domain) | `Jpa…` adapters |
| `LearnerCourses` (level counts, course facts, a page, topics, due cards, review summary) | `JdbcLearnerCourses` |
| `LessonSummaries` (admin list) | `JdbcLessonSummaries` |
| `LessonUsage` (runs, finished lists or cards point at the lesson) | `JdbcLessonUsage` |
| `TextSanitizer` (`<b>`, `<i>`, `<br>` only, no attributes) | `JsoupTextSanitizer` |
| `GrammarDays` (today in the configured zone) | `ZonedGrammarDays` (uses `Clock`) |
| `CurrentUser`, `LanguageCatalog` (shared) via the `Learner` helper | existing |
| `CurrentUser`, `LanguageCatalog` (shared) | existing |

---

## 5. REST API

### 5.1 Learner (`/api/v1/grammar`, token optional unless noted) — built

```
GET  /levels                     → [{level, name, lessonCount}]
GET  /course?level=&topic=&page=0&size=50 (max 100)
                                 → {level, available, finished, next: {slug, title} | null,
                                    review: {due, nextDueOn} | null (guests),
                                    topics: [{key, label}],
                                    lessons: [{slug, position, title, topic {key, label}, minutes,
                                               state: DONE|NEXT|LOCKED, lockedReason?}],
                                    page, size, total}             422 unknown level
GET  /me                         → {level | null}                    (token required)
PUT  /me/level   {level}         → 204                               (token required; 422 level)
GET  /lessons/{slug}?lang=       → {slug, level, position, title, goal, minutes, topic, state,
                                    learn [{icon, label, heading, body?, steps, examples [{ok, text}], tip?}],
                                    worked {question, steps, answer}, practiceCount, checkCount}
                                   401 guest beyond lesson 1 · 403 locked (code TOMORROW | FINISH_PREVIOUS)
                                   · 404 not published / unknown
POST /lessons/{slug}/runs        → 201 {runId, practice: [ItemView], check: [ItemView]}   (same 401/403/404)
POST /runs/{runId}/answers  {itemId, answer: number | string | [string]}
                                 → {correct, expected, why?, explanation, mastered, total, retries, finished}
                                   404 not the caller's run · 409 run finished · 409 lesson updated
                                   · 422 answer / itemId
GET  /review?lang=               → {cards: [{cardId, topic {key, label}, lessonTitle, item: ItemView}],
                                    dueTotal}                        (token required)
POST /review/{cardId}/answer {answer}
                                 → {correct, expected, why?, explanation, box, nextDueOn, changed}
                                                                     (token required; 404 not the caller's)

ItemView = {id, type, prompt, sentence?, wrong?, options?, tiles?}   ← no answer/accept/show/whys/explanation
```

Texts follow the request language (`?lang=`, else the user's support
language, else `Accept-Language`, else English), per text with English as
the fallback.

### 5.2 Admin (`/api/v1/admin/grammar/lessons`, ADMIN or AUTHOR)

```
GET    /?level=&status=          → [{id, level, topic, title, status, position, revision,
                                     checkCount, updatedAt}]
GET    /{slug}                   → AdminLessonView (the lesson format + id, version, status,
                                   revision, position, publishedAt)
POST   /                         → 201 AdminLessonView   body: lesson format
PUT    /{slug}                   → 200 AdminLessonView   body: lesson format + "version"
POST   /{slug}/publish  {position?} → 200 AdminLessonView (body optional; 409 "Position N is already used in the B1 course.";
                                    the same check runs when an edit moves a published lesson)
POST   /{slug}/unpublish         → 200 AdminLessonView
DELETE /{slug}                   → 204 (archived when learners used it)
```

Errors: the shared Problem Details mapping (backend design §9): 422 with
`errors[{field, message}]`, 409 stale version / slug taken / position race.

---

## 6. UI

### 6.1 Pages and files

| Route | Template | Script | Built |
|-------|----------|--------|-------|
| `/grammar` | `grammar.html` (level pick or course) | `js/grammar/course.js` | task 7 |
| `/grammar/lessons/{slug}` | `grammar-lesson.html` | `js/grammar/lesson.js` | task 8 |
| `/grammar/review` | `grammar-review.html` | `js/grammar/review.js` | task 9 |
| `/admin/grammar` | `admin/grammar.html` | `js/admin/grammar-page.js` | task 11 |
| `/admin/grammar/new`, `/admin/grammar/{slug}/edit` | `admin/grammar-edit.html` | `js/admin/grammar-edit.js` | task 11 |

`GrammarPageController` serves the shells (no data). Shared modules:
`js/grammar/api.js` (every `/api/v1/grammar` call via `apiFetch`, plus
`lessonUrl`), `js/grammar/items.js` (task 8: the 4 item types and feedback,
used by lesson and review), `css/grammar.css`. All API text is inserted with
`textContent`, except the allowed `<b>/<i>/<br>` fields, which are built as
DOM nodes from a parsed, allow-listed tree (no `innerHTML` with server
strings). Bar widths are set through `element.style` (the CSP forbids inline
`style` attributes).

The navbar's Grammar item is `new NavItem(GRAMMAR, "Grammar", "/grammar",
true, false)`; the home page's Grammar card links to `/grammar`. A running
lesson sets the navbar's "in progress" guard (task 8).

**Course page (`course.js`, built).** Level choice: signed in → `PUT
/me/level` (a level picked as a guest is saved after signing in when the
server has none); guest → `localStorage["bandup.grammar.level"]`; `?level=`
in the URL wins. Level cards show the published lesson count; a level with
none is disabled ("Coming soon"). The course shows the title ("B1 ·
Intermediate grammar"), "Lesson N · M available" and a bar
(finished / available), the review card (signed in: "N items due · ~M min"
with a Review button, or when the next review is), a sign-in note for
guests, today's lesson (or "unlocks tomorrow" when the daily limit is
reached, or "all done"), topic chips (when there are 2+ topics), the lesson
list (DONE ✓ / NEXT ▶ links; LOCKED 🔒 with its reason) and "Show more
lessons" (pages of 50). Help language: English only in v1.

### 6.2 Colour mapping (prototype → `app.css`)

| Prototype | Orbiko token | Used for |
|-----------|--------------|----------|
| `--brand` purple `#5b4df0` | `--accent` coral | buttons, active pill, progress bars, focus ring |
| `--brand-soft` | `--accent-soft` | kick labels, active pill background, tray tiles |
| `--brand-ink` | `--accent-hover` | bold words in rules, labels |
| `--good` / `--good-soft` | `--ok` / `--ok-soft` | correct answers, ✓ examples, mastery bar, done days |
| `--bad` / `--bad-soft` | `--bad` / `--bad-soft` | wrong answers, ✗ examples, "fix it" sentence |
| `--warn` | `--sun` | tips, "comes back" note, review card border |
| `--bg` / `--surface` / `--surface-2` / `--border` | `--bg` / `--paper` / `--surface` / `--line` | page, cards, inner boxes, borders |
| `--text` / `--muted` / `--faint` | `--fg` / `--muted` / `--muted` at 70% | text |
| Inter | `--ui` (Nunito) for UI, `--display` for headings | fonts |
| `--radius: 18px` | `--radius-lg` (cards), `--radius` (buttons, choices) | shape |

Level cards use the brand palette for variety: A2 `--teal`, B1 `--sky`,
B2 `--grape`, C1 `--sun` (icon + code chip), with 🌱 🌿 🌳 🏔.

The prototype's header, theme button and "🔥 7" chip are dropped (the navbar
and site theme cover them; streaks come later).

### 6.3 Lesson page (`lesson.js` + `items.js`, built)

Goal card ("Start learning" starts the run, the clock and the navbar guard)
→ Learn cards (n / N, Back / Next) → worked example (steps + answer) →
practice items (a wrong answer is explained, not repeated) → mastery queue
(a wrong item goes to the back with "↻ This comes back until you get it
right"; the mastery bar and "finished" come from the server's answers) →
Done card (time, items mastered, retries, the next lesson: "ready now" or
"unlocks tomorrow" for learners, "sign in to unlock the next lessons" for
guests; review note) → guard off. The ✕ button asks before leaving a running
lesson; the navbar and closing the tab do too (`BandUpNav.registerGuard`).

`items.js` renders one item and its feedback for the lesson and the review:
`choose` (option buttons, A–D labels when more than 2; the picked one turns
red/green and the right one green), `type` (sentence with a highlighted gap,
input, Enter or Check), `fix` (the wrong sentence, input), `build` (tap a
tile to move it to the tray, tap again to take it back; Check when all are
used). Feedback: ✓ Correct / ✕ Not quite, "Your answer “x” — why" for a
wrong choice, "Correct: …", the explanation. Lesson texts keep only
`<b>/<i>/<br>` (rebuilt as DOM nodes from a parsed tree); `___` shows as a
blank. Errors: 401 → "Sign in to continue"; 403 → the server's reason; 404 →
"Lesson not found"; 409 during a run → "This lesson changed" + Start again.

### 6.4 Review page (`review.js`, built)

A top bar (✕ back to the course, "🔁 Daily review") and a "Reviewed n / N"
bar. Each due card is shown with `items.js`, labelled "🏷 topic · lesson
title". Feedback adds "⏱ Next review: tomorrow / in 3 days / in 1 week / in
2 weeks" from the server's `nextDueOn`; a wrong card also gets "↻ This comes
back until you get it right" and goes to the back of the session (the server
changes the schedule only on the first answer of the day). Done card:
cards reviewed, retries, and "Review more" when more than 20 were due.
Answers are saved one by one, so there is no leave guard. Errors: 401 →
"Sign in to review"; nothing due → "Nothing to review".

### 6.5 Admin pages (requirement 12, task 11)

Same shell as the reading admin (`admin/layout`, sidebar item `GRAMMAR`
"Grammar" in `AdminNavigationAdvice`, a dashboard card, `guard.js`).
Calls go through `js/admin/grammar-api.js` (`/api/v1/admin/grammar/lessons`).

- **List** (`grammar-page.js`): loads the list once; level / status filters
  and search in the browser; rows sorted by level, then position (drafts
  last). Actions: Edit; Open (published → `/grammar/lessons/{slug}`, new
  tab); Publish (asks for a position, empty = end of the course; a taken
  position shows the server's 409 message); Unpublish; Delete (confirm; when
  the row is still there afterwards: "archived instead"). Archived rows have
  only Edit (read-only).
- **Editor** (`grammar-edit.js`): a monospace JSON textarea and, beside it
  (below on narrow screens), the preview. The JSON shown is the admin view
  without its state fields (`version`, `status`, `revision`, `publishedAt`
  are kept by the page); a new lesson starts from a skeleton of the format.
  The preview re-renders on every change once the JSON parses (else "JSON
  error: …"); it uses `rich()` from `js/grammar/items.js`, so only
  `<b>/<i>/<br>` are kept, and marks right options, accepted answers,
  build answers, whys and explanations. Save → POST (new; then the address
  becomes `/admin/grammar/{id}/edit`) or PUT with `version`; 422 → the list
  of `field — message`; 409 → the server's message. Publish (position input,
  empty = end) saves unsaved changes first. The `id` can't change on an
  existing lesson (checked in the page). Unsaved changes: `beforeunload`,
  the sidebar guard (`setUnsavedCheck`).
---

## 7. Database (Flyway V9 and V10)

`V9__grammar.sql` (built; the file is the source of truth):

| Table | Key points |
|-------|------------|
| `grammar_lesson` | `slug` unique; `level` A2–C1; `topic_key`; English `title`; `position`; `status`; `revision`; `version`; `content` jsonb (body with keys); partial unique index `(level, position)` where PUBLISHED |
| `grammar_progress` | one row per user (`user_id` pk → `app_user`, cascade); chosen `level` |
| `grammar_finished_lesson` | pk `(user_id, lesson_id)`; `user_id` → `grammar_progress` (cascade); `lesson_id` → `grammar_lesson` (restrict); `level`, `first_finished_on`, `times_finished` |
| `grammar_run` | uuid pk; `lesson_id` (restrict); `lesson_revision`; `user_id` null = guest (cascade); `started_at`, `finished_at`; `mastered` jsonb array; `retries`, `answered`; index for the cleanup |
| `grammar_review_card` | uuid pk; `user_id` (cascade); `lesson_id` (restrict); `item_id`; `box` 1–4; `due_on`; `last_answered_on`; unique `(user_id, lesson_id, item_id)`; index `(user_id, due_on)` |

A lesson that runs, finished lists or cards point at can't be deleted (it is
archived instead, requirement 9.6); deleting a user removes their grammar rows.

Adapters (`infrastructure/persistence`): one plain JPA entity per table
(`GrammarLessonEntity`, `GrammarProgressEntity` with `FinishedLessonRow` as a
map keyed by lesson id, `LessonRunEntity`, `ReviewCardEntity`), `LessonJson`
(body ↔ the lesson-format JSON, every text as a language object), `Learners`
(public id ↔ `app_user.id` by SQL), and `Jpa…Repository` adapters. Lesson
saves lock the row and check the version (stale → 409), like exercises; runs,
progress and cards are locked while they change.

`V10__grammar_seed_lessons.sql` (built): the 3 prototype lessons
(`a2-articles-1`, `b1-present-perfect-past-simple-1`,
`b2-second-third-conditional-1`) and a new C1 lesson (`c1-inversion-1`,
inversion after negative adverbials), PUBLISHED, each inserted only if its
slug is free, at the end of its level's course (position 1 on a fresh
database). English only; admins can add `az` texts later through the API.

---

## 8. Security

`GrammarAccessRules`: `GET /api/v1/grammar/levels`, `/course`,
`/lessons/*` and `POST /lessons/*/runs`, `/runs/*/answers` are open (a
token, if sent, identifies the learner); `/api/v1/grammar/me/**` and
`/review/**` need a token. The admin API is covered by the shared
`/api/v1/admin/** → ADMIN or AUTHOR` rule. Run and card ids are random
UUIDs; ownership is checked in the use case (404, never 403, so ids can't be
probed). Day zone and cleanup time: `bandup.grammar.day-zone` (Asia/Baku),
`bandup.grammar.cleanup-cron` (03:45).

## 9. Testing

| Layer | Tests |
|-------|-------|
| domain | `LessonPolicy` (every rule, all violations at once), each item type's grading and normalising, `TileShuffler` never in answer order, `LessonRun` mastery/retries/finish once, `CourseUnlocking` (next, daily limit, replay, guest, inserted lesson), `ReviewCard` boxes and "once per day" |
| persistence | Testcontainers round-trips of every aggregate, JSONB body, position unique index |
| web | MockMvc per endpoint: 401/403/404/409/422, locked lessons by URL, another user's run → 404, revision change → 409 |
| keys | `GrammarKeysTest`: no learner response record before an answer has an `answer`, `accept`, `show`, `whys`, `why`, `expected` or `explanation` field (the worked example's answer and the feedback records excepted); `JdbcLearnerCourses` never reads the items of the body. The API tests also check the JSON of lessons, runs and due cards |
| browser | Selenium: guest opens lesson 1, wrong answer comes back, mastery reaches 100%, Done; signed-in learner sees the review card; review session with cards made due through the database (`due_on`) |
| architecture | existing ArchUnit rules cover the new context |

---

## 10. Decisions

1. **Unlimited, AI-fed course** (user decision): lessons are imported content;
   the course is a query, not a list in code.
2. **C1 in v1** (user decision): 4 levels, seed lesson for each.
3. **One new lesson per day per level**, next lesson unlocks after finishing
   the current one (user approved). Replays are free.
4. **Guests**: lesson 1 of each level only, nothing saved.
5. **`{en, az}` text values in the format now**, Azerbaijani shown later.
6. **Lesson body as JSONB**: the body is always loaded and saved whole and is
   never queried inside; columns hold only what lists and rules need.
7. **Review cards point at item ids**, so item ids are stable; removing a
   check item drops its cards; a lesson with runs or cards is archived, not
   deleted.
8. **Server-side grading per item** (one request per answer), so no key is in
   the browser — the same rule as Reading.
9. **Day zone `Asia/Baku`** by default (`bandup.grammar.day-zone`).
10. **Admin pages in v1** (changed by the user after task 10): n8n imports
    drafts through the API; admins check (JSON editor + preview) and publish
    them in Admin → Grammar (§6.5, task 11).
11. **n8n generator** (requirement 13, `lesson-format.md` "The AI
    generator"): same shape as the reading generator (Claude writes, code
    checks, GPT solves blind, Claude fixes), saving DRAFTs only; the admin
    always publishes. Its code validators repeat `LessonPolicy`, so a
    format change updates both.
