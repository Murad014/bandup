# Requirements — Grammar

## Overview

Grammar is Orbiko's second learner feature, next to Reading. A learner picks a
level and follows a **grammar course**: short daily lessons (~20 min) that
teach one rule, show the reasoning, let the learner practise, and end with a
**mastery check** that must reach 100%. Items from finished lessons come back
later in a **spaced review**.

The behaviour comes from the prototype `ai-docs/grammar/Orbiko — Grammar.html`,
with three changes:

1. **Lessons are content, not a fixed list.** Like reading passages, lessons
   are written by AI (n8n), imported as drafts and published by an admin. A
   course has **no fixed length**: every published lesson of a level joins it.
2. **Answer keys stay on the server.** The browser never receives an answer,
   accepted value, "why" text or explanation before the learner answers.
3. **Orbiko's look**: the app's own colours, fonts, navbar and dark mode.

### Scope

- In scope (v1): level choice (A2, B1, B2, C1), course page, lesson flow,
  spaced review, saved progress, admin import/edit/publish API, the admin
  panel's Grammar section (requirement 12), the lesson JSON format for AI
  generation, the n8n grammar generator (requirement 13), 4 seed lessons
  (one per level).
- Out of scope (later): streaks / XP, showing Azerbaijani help on screen (the format
  carries it already), grammar on the "My progress" page.

### Definitions

- **Level**: A2 Elementary, B1 Intermediate, B2 Upper-Intermediate, C1 Advanced.
- **Lesson**: one rule, made of a goal, learn cards, one worked example,
  practice items and check items (format: `lesson-format.md`).
- **Topic**: a tag on a lesson ("articles", "conditionals"). One topic can
  have many lessons.
- **Course**: all PUBLISHED lessons of one level, ordered by position.
- **Item**: one question. Types: `choose`, `type`, `fix`, `build`.
- **Run**: one pass of a learner through one lesson.
- **Review card**: one check item of a finished lesson, scheduled for review.
- **Day**: a calendar day in the configured zone (default `Asia/Baku`). The
  server counts days ("tomorrow", "in 3 days", "added today"); pages never
  count with the browser's clock, which may be on another day.

---

## Requirement 1 — Choose a level

**User story:** As a learner, I want to choose my level, so that the course
fits me.

#### Acceptance Criteria

1. WHEN a learner opens `/grammar` for the first time THEN the UI SHALL show
   four level cards: A2 Elementary, B1 Intermediate, B2 Upper-Intermediate,
   C1 Advanced, each with the number of published lessons.
2. WHEN a signed-in learner picks a level THEN the system SHALL save it on the
   server, and later visits SHALL open that level's course directly.
3. WHEN a guest picks a level THEN the UI SHALL keep it on this device only.
4. The learner SHALL be able to change the level at any time from the course
   page; progress in every level SHALL be kept.
5. A level with no published lessons SHALL be shown as "coming soon" and SHALL
   NOT be selectable.

## Requirement 2 — Course page

**User story:** As a learner, I want to see my course, so that I know what to
do today.

#### Acceptance Criteria

1. The course page SHALL show the level, "Lesson N · M available", a progress
   bar (finished / available), the review card, the next lesson card and the
   list of lessons.
2. Each lesson in the list SHALL show its position, title, topic and one state:
   DONE (✓), NEXT (▶, clickable) or LOCKED (🔒, with the reason).
3. Finished lessons SHALL stay open to replay.
4. The list SHALL load in pages and SHALL be filterable by topic.
5. WHEN a new lesson is published for the level THEN it SHALL appear in the
   course without any change for the learner (no fixed course length).

## Requirement 3 — Unlocking

**User story:** As a learner, I want one new lesson a day, so that I build a
habit without overload.

#### Acceptance Criteria

1. The NEXT lesson SHALL be the published lesson with the lowest position that
   the learner has not finished.
2. A learner SHALL finish at most **one new lesson per day** (per level); after
   that the NEXT lesson SHALL show "Unlocks tomorrow".
3. Replaying a finished lesson SHALL always be allowed and SHALL NOT count
   towards the daily limit.
4. A guest SHALL be able to open only the first lesson of each level; other
   lessons SHALL show "Sign in to continue".
5. The server SHALL enforce these rules; opening a locked lesson by URL SHALL
   be refused (403, or 401 for a guest).

## Requirement 4 — Lesson flow

**User story:** As a learner, I want to learn the reasoning, practise and
prove I can do it, so that I really master the rule.

#### Acceptance Criteria

1. A lesson SHALL run in stages, shown as pills: Goal → Learn → Practise →
   Master, with a progress bar and a running clock.
2. **Goal**: the lesson title and goal sentence.
3. **Learn**: the learn cards one at a time (icon, label, heading, body,
   optional decision steps, ✓/✗ examples, tip), with Back / Next.
4. **Practise**: first the worked example (question, think-aloud steps,
   answer), then the practice items; a wrong answer is shown and explained but
   not repeated.
5. **Master**: the check items; a wrong item SHALL go to the end of the queue
   and come back until answered correctly ("↻ This comes back until you get it
   right"); a mastery bar SHALL show "mastered / total".
6. **Done**: time, items mastered, retries, the next lesson, and a note that
   the items join the review.
7. Leaving a lesson SHALL ask for confirmation (navbar guard) and SHALL NOT
   count as finished.

## Requirement 5 — Item types and feedback

**User story:** As a learner, I want different kinds of questions and clear
feedback, so that I understand my mistakes.

#### Acceptance Criteria

1. `choose`: 2–4 options (A–D labels when more than 2); one click answers.
2. `type`: a sentence with one gap; Enter or Check answers.
3. `fix`: a wrong sentence; the learner types the correction.
4. `build`: word tiles to put in order; tapping a tile moves it between bank
   and tray; Check is enabled when every tile is used.
5. Every answer SHALL be graded by the server and the UI SHALL show: ✓ Correct
   / ✕ Not quite, the correct answer (when wrong), the explanation, and for a
   wrong `choose` option the "why" of the option picked.
6. Typed answers SHALL be compared ignoring case, leading/trailing spaces,
   repeated spaces, trailing punctuation and curly vs straight apostrophes.
7. Build tiles SHALL be shuffled by the server, never in answer order.

## Requirement 6 — Spaced review

**User story:** As a learner, I want old rules to come back, so that I don't
forget them.

#### Acceptance Criteria

1. WHEN a signed-in learner finishes a lesson THEN its check items SHALL become
   review cards (an item already in the deck is not added twice).
2. The course page SHALL show "N items due · ~M min"; with nothing due it
   SHALL show when the next card is due.
3. A review session SHALL show up to 20 due cards, oldest due first, mixed
   across topics, each labelled with its topic.
4. Scheduling SHALL use 4 boxes: a correct answer moves a card up one box, a
   wrong answer moves it to box 1. Next due: box 1 → tomorrow, box 2 → in 3
   days, box 3 → in 1 week, box 4 → in 2 weeks (a correct answer in box 4
   keeps it in box 4).
5. Only the **first** answer to a card on a given day SHALL change its
   schedule; a wrong card repeats in the session until correct, without
   further schedule changes.
6. Feedback SHALL show "⏱ Next review: …".
7. Guests SHALL have no review deck (the UI invites them to sign in).

## Requirement 7 — Answer keys never reach the browser early

#### Acceptance Criteria

1. The learner lesson and review endpoints SHALL NOT return answers, accepted
   values, option "whys" or explanations.
2. The worked example (a teaching step) SHALL be returned with its answer.
3. An answer endpoint SHALL return the feedback for the one item answered only.
4. A test SHALL check every learner query and response shape for key fields.

## Requirement 8 — Progress is saved

#### Acceptance Criteria

1. For a signed-in learner the server SHALL keep: the chosen level, finished
   lessons with the day first finished, and the review deck.
2. A run SHALL belong to its learner; another user's run SHALL be not found
   (404). A guest run SHALL be reachable only by its unguessable id.
3. A run SHALL record the lesson revision it was started on; editing a lesson
   SHALL NOT break runs in progress (answers are graded against the run's
   revision while it is still the stored one, otherwise the run must restart:
   409 "This lesson was updated, please start again").
4. Unfinished runs older than 7 days SHALL be deleted by a cleanup job.

## Requirement 9 — Lessons are imported, edited and published (AI pipeline)

**User story:** As an admin, I want AI-written lessons to arrive as drafts, so
that I can check and publish them and the course keeps growing.

#### Acceptance Criteria

1. ADMIN or AUTHOR SHALL be able to create a lesson draft from the JSON format
   (`POST /api/v1/admin/grammar/lessons`), list, get, edit (with `version`),
   publish, unpublish and delete lessons.
2. Every save SHALL validate the lesson against the format rules and SHALL
   report **every** problem at once (422, JSON path per field), so an AI
   pipeline can retry with the errors.
3. Publishing without a position SHALL put the lesson at the end of its
   level's course.
4. Two published lessons of one level SHALL NOT share a position.
5. Editing a published lesson SHALL keep it valid and SHALL raise its revision.
6. Deleting a lesson that learners have runs or review cards for SHALL archive
   it instead; archived lessons leave the course, finished ones stay counted.
7. Removing an item from a published lesson SHALL drop its review cards.
8. Text fields SHALL accept a small HTML subset (`<b>`, `<i>`, `<br>`); all
   other markup SHALL be removed by the sanitizer.

## Requirement 10 — Look and feel

#### Acceptance Criteria

1. Grammar pages SHALL use the shared layout and navbar; the navbar's Grammar
   item SHALL become an enabled link (no "soon" badge).
2. Colours, fonts, radius and shadows SHALL come from `app.css` tokens (see
   design §6); the prototype's purple palette and own header SHALL NOT be used.
3. Light and dark themes SHALL work through the site theme.
4. The pages SHALL work at phone width (≥ 320 px) and by keyboard (choices,
   tiles and buttons are focusable; Enter submits typed answers).
5. No fake data: the prototype's "🔥 7" streak SHALL NOT be shown.

## Requirement 11 — Multilingual help (format now, UI later)

#### Acceptance Criteria

1. Every learner-facing text in the lesson format SHALL accept `{"en": …,
   "az": …}`; `en` is required, other languages optional.
2. The learner API SHALL pick the language with the existing request-language
   rules, falling back to `en`.
3. v1 UI SHALL show English only.

## Requirement 12 — Grammar in the admin panel

**User story:** As an admin, I want a Grammar section in the admin panel, so
that I can check the lessons n8n generates and publish them, like reading
sessions.

#### Acceptance Criteria

1. The admin sidebar and dashboard SHALL have a **Grammar** item
   (`/admin/grammar`) for ADMIN and AUTHOR accounts.
2. The list SHALL show every lesson (level, position, title, topic, status
   with revision, check items, last change), filterable by level and status
   and searchable by title, topic or id.
3. From the list an admin SHALL be able to open a lesson, create a new one,
   publish (at the end of the course or at a chosen position), unpublish and
   delete (archived instead when learners used it; the list says so).
4. The editor SHALL edit the lesson as the JSON of `lesson-format.md` (the
   same JSON n8n sends) and SHALL show a live **preview** of it: goal, learn
   cards, worked example, and every practice and check item **with its
   answers, "whys" and explanation**, so the admin can check them.
5. Invalid JSON SHALL be reported before sending (with its position); on save
   every server error SHALL be listed with its JSON path.
6. Saving a new lesson SHALL create a DRAFT; saving an existing one SHALL
   send its loaded `version` (stale → "changed by someone else").
7. The editor SHALL publish (optional position) and unpublish; an archived
   lesson SHALL be shown read-only.
8. Leaving the editor with unsaved changes SHALL ask first.

## Requirement 13 — n8n grammar generator

**User story:** As an admin, I want n8n to write grammar lessons for me, so
that the course keeps growing and I only check and publish.

#### Acceptance Criteria

1. A form SHALL ask for the level, topic (optional topic key), the one rule to
   teach, the lesson number in the topic, Azerbaijani help (yes/no) and notes.
2. The lesson id SHALL be `<level>-<topic key>-<lesson no>` (e.g.
   `b1-present-perfect-1`).
3. An AI SHALL write the lesson in the format of `lesson-format.md`; a code
   step SHALL check every format rule before anything is sent and give the
   AI the problems to fix (at most 2 retries).
4. A second AI SHALL solve every item blind (no answers); an item it answers
   differently, finds a second correct answer for, or reports a problem with
   SHALL go back to the writer to fix (at most 2 fixes).
5. The lesson SHALL be saved in Orbiko as a DRAFT through
   `POST /api/v1/admin/grammar/lessons` (ADMIN or AUTHOR login); Orbiko's
   errors SHALL stop the flow with every problem shown.
6. The exported workflow in the repo SHALL hold no passwords or server
   addresses (placeholders only).
