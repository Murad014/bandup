# Requirements — Database (content, learners, attempts)

## Overview

BandUp moves from in-browser stub data to a real relational database. The
schema must serve today's Reading feature and the admin panel, and must not
need to be redesigned when BandUp goes **public and global**: learners with
many native languages, more exam skills (Listening, Grammar, …) and
eventually more target languages than English.

This spec covers the **persistent data model** only: tables, keys,
constraints, how translations work, and how the existing API contracts map
onto the tables. The REST controllers, the sanitizer, and authentication are
covered by their own specs. This spec only fixes the tables they rely on.

### Definitions

- **Target language**: the language being learned or tested (the passage, the
  question stems, the options, the answers). It is English today.
- **Support language**: the learner's own language, used for help text:
  glosses, explanations, stage titles, translated instructions. It is
  Azerbaijani today. Any number of these may exist later.
- **Exercise**: one practice unit (today, one Reading session: a passage, its
  questions, and its timer).
- **Gloss**: the meaning of a marked word in the passage, in a support
  language.

---

## Requirement 1 — Language-neutral content model

1. THE schema SHALL keep exam content (in the target language) apart from help
   text (in support languages).
2. THE schema SHALL let a support language be added without any schema change
   or migration (it becomes a row in `language`).
3. THE passage HTML SHALL NOT contain words of any support language. A glossed
   word SHALL reference a gloss key (`data-g="g7"`), and its meanings SHALL be
   stored per language.
4. WHEN help text is requested in a language that has no translation THEN the
   system SHALL fall back to the exercise's fallback language (English),
   item by item.
5. EVERY language-bearing column SHALL reference `language(code)` (BCP-47).
   The `language` table SHALL record the writing direction (LTR or RTL).
6. THE schema SHALL allow more target languages later (`exercise.target_language`).

## Requirement 2 — Exercises and question types

1. THE schema SHALL store exercises independently of skill, with a `skill`
   discriminator and one detail table per skill (`reading_passage` now,
   `listening_audio` later).
2. THE schema SHALL support every question family in `admin/questionTypes.js`
   (choiceFixed, gap, mcSingle, mcMulti, match), including options at group
   level and at question level.
3. Question numbers (1..N) SHALL be derived from the order of groups and
   questions. They SHALL NOT be stored, so reordering cannot leave gaps or
   duplicates.
4. Evidence references SHALL be enforced with a foreign key, not checked only
   in application code.

## Requirement 3 — Integrity of answers

1. THE answers, accepted values and explanations SHALL live in tables that the
   learner read path never selects before submission.
2. THE database SHALL reject impossible states, such as negative durations, a
   low-time threshold greater than the duration, an option owned by both a
   group and a question, or a duplicate option key.

## Requirement 4 — Lifecycle and history

1. Exercises SHALL have a status: DRAFT, PUBLISHED or ARCHIVED. Learners SHALL
   see PUBLISHED exercises only.
2. An exercise that has attempts SHALL NOT be hard-deleted. It SHALL be
   archived instead.
3. Each attempt SHALL record the content revision it was graded against, and
   for each answer the value given, whether it was correct, and the expected
   answer, so later edits cannot rewrite history.
4. Concurrent admin edits SHALL be detected with optimistic locking.

## Requirement 5 — Learners and roles

1. THE schema SHALL store users with a UI language, a support language and a
   target language.
2. Attempts SHALL be possible without an account (`user_id` is NULL) so the
   current anonymous flow keeps working.
3. Roles SHALL be data (LEARNER, AUTHOR, TRANSLATOR, ADMIN). A TRANSLATOR can
   edit translation rows without changing exam content.

## Requirement 6 — IELTS exam rules

1. A gap-type group SHALL be able to store a word limit (`max_words`) and
   whether numbers are allowed in addition to it. WHEN an answer exceeds the
   limit THEN it SHALL be graded wrong even if the words match.
2. A matching group SHALL record whether options may be reused. WHERE they may
   not be, publish validation SHALL reject two questions with the same answer.
3. Summary completion SHALL support both typing words from the text
   (`SUMMARY`) and choosing them from a box (`SUMMARY_BOX`).
4. Diagram label completion (`DIAGRAM_LABEL`) SHALL reference an image. Image
   and audio files SHALL be stored outside the database, with metadata in
   `media` and alt text per language in `media_translation`.
5. A "Choose N" multiple-answer question SHALL be worth N points, earn 1 point
   per correct option, and occupy N question numbers (e.g. Q7–8). Attempt
   scores SHALL be stored as points.
6. An exercise's timer SHALL be optional (`duration_seconds` may be NULL).

## Requirement 7 — Standalone exercises, browse and filter

1. An admin SHALL be able to create and publish an exercise on its own,
   without a course or stage. Course and stage SHALL be optional grouping only.
2. Exercises SHALL NOT be locked. Any learner (anonymous or logged in) SHALL be
   able to open and submit any PUBLISHED exercise.
3. Each exercise SHALL record a format (`MINI` or `FULL`), an optional CEFR
   level (A1–C2), and zero or more topics with names per language.
4. Learners SHALL be able to filter published exercises by skill, format,
   level, question type, maximum duration and topic, combined with AND, and
   (when logged in) hide exercises they have already attempted.

## Non-functional requirements

1. PostgreSQL in all environments. Schema changes go only through versioned
   Flyway migrations, and Hibernate runs with `ddl-auto=validate`.
2. All text is UTF-8 (`text`). Timestamps are `timestamptz` in UTC.
3. Public identifiers are slugs (exercises) or UUIDs (attempts, users). Internal
   bigint keys are never part of the public API.
4. UI chrome strings (button labels, etc.) are NOT stored in the database. They
   live in message bundles in the repository.
