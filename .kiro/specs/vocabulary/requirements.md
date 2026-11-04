# Requirements — Vocabulary

## Overview

Vocabulary is Orbiko's third learner feature, next to Reading and Grammar. A
learner **saves words** they want to learn: from a reading passage (a dotted
word, or any word they select) or by typing them. Saved words are taught in
short **learn sessions** (meet → practise → master, like a grammar lesson)
and come back in a **spaced review** until they stick.

Every word has one shared **word card** (meaning, Azerbaijani, examples,
word pairs, task material). The **database is always checked first**; a card
is written by AI (OpenRouter, called by the app) **only once per word**, the
first time any learner saves it. Every later learner gets it from the
database.

The UI follows the approved prototype `ai-docs/vocabulary/Orbiko — Vocabulary.html`
(screens 1–4), with Orbiko's look.

### Scope

- In scope (v1): saving words (reading page and typed), the shared word bank
  with AI cards, My words page, learn sessions, review, the admin Vocabulary
  section (check and fix cards), the card JSON format (`card-format.md`).
- Out of scope (later): word packs (prototype screen 5), recorded audio
  (v1 uses the browser's speech), several meanings per word (decision 4),
  streaks, vocabulary on "My progress".

### Definitions

- **Word card**: the shared description of one English word (its base form,
  e.g. `riverbank`), with its other forms (`riverbanks`).
- **Saved word**: one learner's link to a card, with its learning state.
- **States**: NEW (saved, not learned) → LEARNING (boxes 1–3) → MASTERED
  (answered right in box 4).
- **Learn session**: up to 5 NEW words taught and checked in one go.
- **Review session**: up to 20 due words.
- **Recall session**: up to 10 learned words, Azerbaijani → English, practice
  only.
- **Day**: a calendar day in the configured zone (default `Asia/Baku`). The
  server counts days ("tomorrow", "in 3 days", "added today"); pages never
  count with the browser's clock, which may be on another day.

---

## Requirement 1 — Save a word from a passage

**User story:** As a learner reading a passage, I want to save words I don't
know, so that I can learn them later.

#### Acceptance Criteria

1. WHEN a learner taps a dotted word THEN its popover SHALL show the meaning
   (as today) and **＋ Add to my words** (or "✓ In my words" with Remove).
2. WHEN a learner selects 1–3 words in the passage THEN the selection bar
   SHALL offer **＋ Add to my words** next to Highlight.
3. The saved word SHALL keep where it came from: the passage and the
   sentence it was in (shown as "from: …" and as the first example).
4. Saved words SHALL be marked in the passage (teal), during and after the
   test, and a box SHALL list the passage's NEW words with "Learn these
   words".
5. A guest SHALL be asked to sign in to save words (decision 3).

## Requirement 2 — Save a word by typing

1. My words SHALL have an input "Add a word yourself"; Enter or ＋ Add saves.
2. Input SHALL be 1–3 English words (letters, `'` and `-`), at most 40
   characters; anything else SHALL be refused before any lookup (422).

## Requirement 3 — Database first, AI once per word

1. The typed or selected text SHALL be normalised (lower case, trimmed,
   single spaces, curly → straight apostrophe).
2. The system SHALL look the text up among the forms of existing cards
   (`riverbanks` → card `riverbank`). WHEN found THEN the word SHALL be saved
   at once and **no AI call SHALL be made**.
3. WHEN not found THEN the system SHALL create the card as PREPARING, save the
   learner's word at once ("Preparing… ⏳"), and ask the AI for the card in
   the background.
4. The AI SHALL return the base form; WHEN a card for that base form already
   exists THEN the new form SHALL be added to it, the learner's word SHALL
   point to it, and the AI card SHALL NOT replace it.
5. Two learners saving the same new word at the same time SHALL cause **one**
   AI call.
6. WHEN the AI says the text is not an English word (or a name, or rude) THEN
   the card SHALL be stored as REJECTED, the learner's word removed, and the
   learner told; the same text SHALL NOT call the AI again.
7. A card SHALL be checked by code (`card-format.md`) before it becomes READY;
   an invalid answer SHALL be retried once with the problems; after that the
   card SHALL be FAILED and the learner can press "Try again".
8. A learner SHALL be able to trigger at most **30** AI cards per day
   (`bandup.vocabulary.ai.daily-limit`), counted when a new card is created
   for them (and on "Try again"), so fast repeated requests can't pass it;
   words already in the database SHALL NOT count. Over the limit → 429 "You
   can add more new words tomorrow".
9. WHEN no AI key is configured THEN words in the database SHALL still work
   and new words SHALL be refused with 503 "New words can't be prepared right
   now".
10. A card for a dotted passage word SHALL be written with that passage's
    sentence and meaning as context.

## Requirement 4 — My words

1. `/vocabulary` SHALL show totals (all, new, learning, mastered), a Learn
   card ("N new words ready", up to 5) and a Daily review card ("N words
   due · ~M min"), the add input, tabs (All / New / Learning / Mastered),
   search and the list.
2. Each row SHALL show the word, part of speech and level, the meaning, where
   it came from and when, its strength (4 bars + "tomorrow / in 3 days / in 1
   week / in 2 weeks", or NEW, or ✓ MASTERED, or "Preparing… ⏳" / "Couldn't
   prepare — Try again") and Remove.
3. The navbar SHALL have an enabled **Vocabulary** item with the number of NEW
   words.
4. Removing a word SHALL remove only the learner's saved word, never the card.

## Requirement 5 — Learn session

1. A learn session SHALL take up to **5** NEW, READY words (oldest saved
   first, or the words of one passage when started from it); there is no
   daily cap.
2. Stages SHALL be shown as pills: Goal → Meet → Practise → Master, with a
   progress bar.
3. **Meet**: one card per word: the word, 🔊 (browser speech), IPA, part of
   speech, level, meaning, Azerbaijani, the learner's own passage sentence (if
   any) and the card's examples, word pairs, word family; Back / Next.
4. **Practise** (not repeated): choose the meaning of a word; match all
   session words to their meanings (3+ words).
5. **Master**: one task per word (gap, type from meaning with first-letter
   hint, listen and type, word pair, Azerbaijani → English); a wrong task
   SHALL go to the end and come back ("↻ This word comes back until you get it
   right"); a mastery bar "n / N".
6. **Done**: the words learned, tasks and retries, "they come back tomorrow",
   and "Learn N more" when NEW words are left.
7. At the end every session word SHALL become LEARNING, box 1, due tomorrow.
8. Leaving a session SHALL ask first; words already mastered in it are not
   lost (decision 6).

## Requirement 6 — Review

1. A review session SHALL show up to **20** due words, oldest due first, each
   labelled with where it came from (never with the word itself).
2. Boxes as in Grammar: right → up one box, wrong → box 1; due: box 1 →
   tomorrow, 2 → 3 days, 3 → 1 week, 4 → 2 weeks; right in box 4 →
   MASTERED (no longer due).
3. Only the first answer of a word on a day SHALL change its schedule; a wrong
   word repeats in the session without further changes.
4. Feedback SHALL show "⏱ Next review: …" and the box change.

## Requirement 7 — Answers stay on the server

1. Session tasks SHALL be built and graded on the server; task payloads SHALL
   NOT contain the answer, accepted values or the "why" texts.
2. Review tasks SHALL NOT reveal the word in labels or hints beyond the first
   letter.
3. A test SHALL check every learner response shape for key fields.

## Requirement 8 — Admin: word cards

1. Admin → Vocabulary SHALL list cards (word, level, status, learners who
   saved it, source AI/ADMIN, updated) with search and status filter.
2. An admin SHALL be able to edit a card (form; checked by the same rules;
   `version` for conflicts), **regenerate** it with AI, add/remove forms, and
   delete a card nobody has saved.
3. An edit SHALL apply to every learner at once.

## Requirement 9 — Look and feel

1. Pages SHALL use the shared layout, navbar and `app.css` tokens; light and
   dark themes; phone width (≥ 320 px); keyboard use (Enter submits typed
   answers; options and tiles focusable).
2. All card texts are plain text (no HTML), inserted with `textContent`.

## Requirement 11 — Recall (Azerbaijani → English)

**User story:** As a learner, I want to see a word in my own language and
type it in English, so that I can check I really remember my words.

#### Acceptance Criteria

1. My words SHALL have a third card **"Recall · 🇦🇿 → 🇬🇧"** next to Learn and
   Daily review: "N words, mixed" (N = learned words, at most 10) with **Start
   recall**, or "Nothing to recall yet" with the button hidden.
2. A recall session SHALL take up to **10** learned words (LEARNING or
   MASTERED, READY cards) **at random**, a different mix each time, with at
   most one word per Azerbaijani meaning.
3. Each task SHALL show the Azerbaijani meaning, part of speech and level,
   and **no hint** (no first letter, no length); the learner types the English
   word. "I don't remember — show me" SHALL count as a wrong answer.
4. Accepted answers: the word and its forms, case and spaces ignored; also
   the learner's other learned words with the same Azerbaijani meaning.
5. Right → ✓ with the word, its meaning and 🔊; wrong → the right word, and
   the word comes back at the end until typed right; a "Recalled n / N" bar.
6. **Done**: "n / N right on the first try", the missed words, a note that
   review days haven't changed, **Again with other words** and My words.
7. A recall session SHALL NOT change any word's state, box or due day; it can
   be played any time, as often as the learner likes. Leaving it asks nothing.
8. Answers SHALL be graded on the server as in requirement 7 (the English word
   is never in the task payload).

## Requirement 10 — Security and cost

1. The OpenRouter key SHALL be a server setting (`BANDUP_OPENROUTER_API_KEY`),
   never committed, logged or sent to the browser.
2. Learner text SHALL reach the AI only after the input rules (req. 2.2); the
   AI answer SHALL be parsed as JSON and checked; nothing from it is run or
   rendered as HTML.
3. Every AI call SHALL be logged without the key: word, model, duration,
   result (READY / REJECTED / FAILED), tokens if returned.
