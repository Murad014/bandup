# Requirements — Vocabulary

## Overview

Vocabulary is Orbiko's third learner feature, next to Reading and Grammar. A
learner **saves words** they want to learn: from a reading passage (a dotted
word, or any word they select) or by typing them. Saved words are taught in
short **learn sessions** (meet → practise → master, like a grammar lesson)
and are then **mastered set by set**: learned words, 25 at a time,
Azerbaijani shown, English typed, until every word of the set is sure
(requirement 6; it replaced the daily review and Recall).

Every word has one shared **word card** (meaning, Azerbaijani, examples,
word pairs, task material). The **database is always checked first**; a card
is written by AI (OpenRouter, called by the app) **only once per word**, the
first time any learner saves it. Every later learner gets it from the
database.

The UI follows the approved prototype `ai-docs/vocabulary/Orbiko — Vocabulary.html`
(screens 1–3 and 7 — Sets; screens 4 Review and 6 Recall are kept only for
comparison), with Orbiko's look.

### Scope

- In scope (v1): saving words (reading page and typed), the shared word bank
  with AI cards, My words page, learn sessions, review, the admin Vocabulary
  section (check and fix cards), the card JSON format (`card-format.md`).
- Out of scope (later): word packs (prototype screen 5), recorded audio
  (v1 uses the browser's speech), several meanings per word (decision 4),
  streaks, vocabulary on "My progress".

### Definitions

- **Word card**: the shared description of one English word or short phrase
  (its base form, e.g. `riverbank`, `vicious circle`), with its other forms
  (`riverbanks`, `vicious circles`).
- **Saved word**: one learner's link to a card, with its learning state.
- **States**: NEW (saved, not learned) → LEARNING (learned, in a set, not
  mastered yet) → MASTERED (typed right on the first try in 2 rounds in a
  row).
- **Learn session**: up to 5 NEW words taught and checked in one go.
- **Set**: 25 learned words in the order they were learned (the last set may
  be smaller). **Current set**: the first set with a word not mastered.
- **Round**: one pass over the current set's words that aren't mastered yet,
  Azerbaijani → English.
- **Day**: a calendar day in the configured zone (default `Asia/Baku`). The
  server counts days ("added today", the daily AI limit); pages never count
  with the browser's clock, which may be on another day.

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

## Requirement 2 — Save a word or phrase by typing

1. My words SHALL have an input "Add a word or phrase, e.g. "vicious circle""; Enter or ＋ Add saves.
2. Input SHALL be 1–3 English words (letters, `'` and `-`), at most 40
   characters — a word or a short phrase ("vicious cycle", "take for
   granted"); anything else SHALL be refused before any lookup (422).

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
11. A phrase SHALL get a card like a word: its base form and forms are whole
    phrases. In its gap and word-pair tasks the blank MAY hold one word of the
    phrase ("a ___ cycle of debt" → "vicious") when the completed sentence
    contains the phrase; for a one-word card the blank SHALL hold one of its
    forms, as before.

## Requirement 4 — My words

1. `/vocabulary` SHALL show totals (all, new, learning, mastered), a Learn
   card ("N new words ready", up to 5) and a **Sets** card ("Set 2 of 3 ·
   18 / 25 mastered", a dot per word, "Continue Set 2 →"; "No sets yet" until
   a word is learned), the add input, tabs (All / New / Learning / Mastered),
   search and the list.
2. Each row SHALL show the word, part of speech and level, the meaning, where
   it came from and when, its progress ("Set 2 · not yet" / "Set 2 · right
   once", or NEW, or ✓ MASTERED, or "Preparing… ⏳" / "Couldn't prepare — Try
   again") and Remove.
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
6. **Done**: the words learned, tasks and retries, "they join your sets",
   and "Learn N more" when NEW words are left.
7. At the end every session word SHALL become LEARNING (it joins the sets,
   after every word learned before it).
8. Leaving a session SHALL ask first; words already mastered in it are not
   lost (decision 6).

## Requirement 6 — Sets (replaces the daily review and Recall)

**User story:** As a learner with many words, I want to work through them
step by step, 25 at a time, so that every word gets covered and I'm sure I
know it before it counts as mastered.

#### Acceptance Criteria

1. Learned words (LEARNING or MASTERED) SHALL be split into sets of **25** in
   the order they were learned (words learned in one session by the order
   they were saved). The last set MAY be smaller and fills up as words are
   learned. Removing a word moves the later words up.
2. The **current set** SHALL be the first set with a word not mastered; sets
   before it are mastered, sets after it are locked.
3. `/vocabulary/sets` SHALL list the sets: number, size ("4 / 25 words" while
   filling), a dot per word (○ not yet, ◐ right once, ● mastered), and
   "✓ Mastered · in N rounds", "n / 25 mastered" with **Start round N →**, or
   "🔒 Locked · opens when Set n is mastered"; a legend and the rule.
4. A **round** SHALL ask every not-mastered word of the current set once, in
   random order: the Azerbaijani meaning, part of speech and level, **no
   hint**; the learner types the English. "I don't remember — show me" SHALL
   count as wrong. A wrong word SHALL come back at the end of the round until
   typed right. A word whose card isn't READY is skipped.
5. Accepted answers: the word or phrase and its forms, case and spaces
   ignored; also the learner's other learned words with the same Azerbaijani
   meaning.
6. Only the **first answer of a word in a round** SHALL count: right → right
   once (◐), or **MASTERED** (●) when it was right once in the round before;
   wrong → back to not yet (○). Each answer is saved at once; leaving a round
   asks nothing and keeps the answers.
7. Feedback: ✓ / ✕, the word, 🔊, and the step ("◐ Right once — get it right
   in the next round to master it", "● Mastered! It won't be asked again",
   "○ Missed — it needs 2 rounds in a row again"); a bar "Answered n / N".
8. **Round done**: "n / N right on the first try", the words mastered now,
   right once and missed, the set's dots and "n / 25 mastered", then **Next
   round →**, or "🎉 Set n mastered!" with **Start Set n+1 →** (or "you
   mastered every learned word"), All sets and My words.
9. Mastered words SHALL NOT be asked again.
10. No learned word → 422 "Learn some words first — they join Set 1."; every
    word mastered → 422 "Every set is mastered. Learn new words to start the
    next set."
11. Words learned before this change keep their order by when they were
    saved; MASTERED words stay mastered, LEARNING words start at not yet.
    Review boxes and due days are no longer used.

## Requirement 7 — Answers stay on the server

1. Session tasks SHALL be built and graded on the server; task payloads SHALL
   NOT contain the answer, accepted values or the "why" texts.
2. Round tasks SHALL NOT reveal the word (no hint, no label naming it).
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

## Requirement 11 — Recall (Azerbaijani → English) — replaced

Recall (10 random learned words, practice only) was replaced by Sets
(requirement 6) at the user's request: random words never covered every word,
and nothing became mastered. `/vocabulary/recall` and `/vocabulary/review`
open `/vocabulary/sets`.

## Requirement 10 — Security and cost

1. The OpenRouter key SHALL be a server setting (`BANDUP_OPENROUTER_API_KEY`),
   never committed, logged or sent to the browser.
2. Learner text SHALL reach the AI only after the input rules (req. 2.2); the
   AI answer SHALL be parsed as JSON and checked; nothing from it is run or
   rendered as HTML.
3. Every AI call SHALL be logged without the key: word, model, duration,
   result (READY / REJECTED / FAILED), tokens if returned.
