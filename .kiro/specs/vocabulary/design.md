# Design — Vocabulary

## 1. Decisions at a glance

| Topic | Decision |
|-------|----------|
| Context | New backend context `com.bandup.api.vocabulary`; never imports Practice, Content or Grammar classes (ArchUnit rule 5) |
| Word bank | One shared **word card** per base form (lemma), with its forms; content as JSONB (`card-format.md`) |
| AI | The app calls **OpenRouter** itself (JDK `HttpClient`, no new library) — only when no card has the word |
| DB first | Lookup by normalised form → found: no AI; not found: one PREPARING card, AI in the background |
| One call per word | Unique form + row lock; the second learner waits on the same PREPARING card |
| Learner state | 2 aggregates: `SavedWord` (state + boxes, one per learner and card) and `WordSession` (learn or review) |
| Grading | On the server, one task per request; tasks are built from cards and snapshotted in the session |
| Days | `LocalDate` in `bandup.vocabulary.day-zone` (default `Asia/Baku`), from the injected `Clock` |
| Guests | Can read cards in popovers; saving needs sign-in |
| Cost | ≤ 30 AI cards per learner per day; cheap model by default (`bandup.vocabulary.ai.model`) |
| UI | Thymeleaf shells + ES modules (`js/vocabulary/*`), `app.css` tokens, prototype screens 1–4 |

---

## 2. Packages

```
com.bandup.api.vocabulary
├── domain/
│   ├── word/
│   │   ├── aggregate/      WordCard
│   │   ├── valueobject/    WordId, Lemma, WordForm (normalised text), CardStatus
│   │   │                   (PREPARING | READY | FAILED | REJECTED), CardSource (AI | ADMIN),
│   │   │                   CardContent (pos, level, ipa, meaning, examples, collocations, family,
│   │   │                   gap, pair, wrongMeanings), Meaning (en, az), GapTask, PairTask, CefrLevel
│   │   ├── service/        WordNormaliser, WordGate (input rules, before any lookup), CardPolicy
│   │   └── repository/     WordCardRepository
│   ├── saved/
│   │   ├── aggregate/      SavedWord
│   │   ├── valueobject/    SavedWordId, LearnerId, CardRef, WordState (NEW | LEARNING | MASTERED),
│   │   │                   Box, ReviewSchedule, WordSource (passage slug + sentence | typed)
│   │   └── repository/     SavedWordRepository
│   └── session/
│       ├── aggregate/      WordSession
│       ├── valueobject/    SessionId, SessionKind (LEARN | REVIEW), Task (sealed: ChooseMeaning,
│       │                   Match, Gap, TypeFromMeaning, Listen, Pair, Translate), TaskId,
│       │                   GivenAnswer, TaskFeedback, SessionWord (snapshot of a card for the session)
│       ├── service/        TaskBuilder (cards → tasks), AnswerNormaliser
│       └── repository/     WordSessionRepository
├── application/            SaveWord, CardWriter (AI call outside transactions), CardResults (stores
│   │                       answers; joins cards), CardRetryJob, SessionCleanup, MyWords (page, polling,
│   │                       remove, try again), WordLookup, WordSessions, WordCardAdmin, WordViews,
│   │                       VocabularyRules (shared domain services), Learner; view/VocabularyViews
│   └── port/               WordCardAi (write a card), AiUsage (daily count), PassageContext
│                           (dotted-word meaning + title), LearnerWords (read model), CardQueue
│                           (write after commit), VocabularyDays, VocabularySettings, CardSummaries
├── infrastructure/
│   ├── ai/                 OpenRouterWordCards (HttpClient, JSON parse), CardPrompt, AiProperties
│   ├── persistence/        WordCardEntity (forms with plain SQL), SavedWordEntity, WordSessionEntity,
│   │                       Jpa…Repository adapters, CardJson, SessionJson, Learners
│   ├── readmodel/          JdbcLearnerWords, JdbcAiUsage, JdbcPassageContext, JdbcCardSummaries
│   ├── config/             VocabularyConfig (settings), ZonedVocabularyDays,
│   │                       AfterCommitCardQueue (private 2-thread pool), CardRetrySchedule
│   └── security/           VocabularyAccessRules
└── web/                    WordController, WordSessionController, AdminWordCardController

com.bandup.ui               VocabularyPageController (/vocabulary, /vocabulary/learn, /vocabulary/review);
                            admin: /admin/vocabulary, /admin/vocabulary/{id}/edit
```

Lombok as in the other contexts; no `package-info.java`. Own value objects
per module (ArchUnit "only *Id across modules"): `saved` and `session` refer to
a card by `CardRef`/`WordId`, never to `WordCard`.

---

## 3. Domain (tactical model)

### 3.1 `word/` — WordCard (aggregate root)

```
WordCard   @AggregateRoot   equal by WordId
  id: WordId   lemma: Lemma (empty while PREPARING)   forms: Set<WordForm>
  status: CardStatus   content: CardContent?   source: CardSource   revision
  attempts: int   lastError?   version
  preparing(id, form) → PREPARING card owning that one form
  ready(lemma, forms, content, CardPolicy)   — PREPARING/FAILED → READY (policy must pass)
  failed(error)        — attempts+1; after 2 → FAILED
  rejected(reason)     — REJECTED; keeps its form so the text never calls AI again
  retry()              — FAILED → PREPARING (learner "Try again", admin "Regenerate")
  edit(content, CardPolicy) — admin; READY stays READY; revision+1; source ADMIN
  addForms(forms) / removeForm(form) (never the lemma)
```

`CardPolicy` checks `card-format.md` and reports every problem with its
path. `WordGate` refuses input before any lookup (req. 2.2).
`WordNormaliser`: lower case, trim, single spaces, `’` → `'`.

### 3.2 `saved/` — SavedWord (aggregate root, one per learner and card)

```
SavedWord   @AggregateRoot   equal by SavedWordId
  learner: LearnerId   card: CardRef   source: WordSource   addedOn
  state: NEW | LEARNING | MASTERED   box: Box (1–4)   dueOn?   lastAnsweredOn?
  learned(today)                 — NEW → LEARNING, box 1, due tomorrow
  answer(correct, today) → ReviewSchedule
                                 — first answer of the day only: right → box+1 (box 4 right → MASTERED),
                                   wrong → box 1; due +1/3/7/14 days
  pointTo(CardRef)               — when the AI's lemma joins an existing card
```

### 3.3 `session/` — WordSession (aggregate root)

```
WordSession   @AggregateRoot   equal by SessionId
  learner   kind: LEARN | REVIEW   words: List<SessionWord> (snapshots)   tasks: List<Task>
  queue (task ids)   mastered: Set<TaskId>   retries   finished
  answer(taskId, GivenAnswer, normaliser) → TaskFeedback
     — practise tasks: graded, not repeated; master/review tasks: wrong → back of the queue
  finished when every master/review task is mastered
```

`TaskBuilder` (domain service) builds tasks from `SessionWord` snapshots:
LEARN → 1 ChooseMeaning (options = meaning + the card's 3 `wrongMeanings`),
1 Match (3+ words), then one master task per word rotating Gap /
TypeFromMeaning / Listen / Pair / Translate (Translate only when `az` is
there). REVIEW → one task per word, rotating by box. Options are shuffled per
session (seeded by the session id, as Grammar's tiles).

---

## 4. Saving a word (the important flow)

```
POST /api/v1/vocabulary/words {text, source?}
  1 WordGate        refuse junk (422) — no lookup, no AI
  2 normalise       "Riverbanks " → "riverbanks"
  3 lookup form     vocab_word_form (unique) → card?
      READY / PREPARING / FAILED → SavedWord (unique learner+card; again → 200 "already saved")   ← NO AI
      REJECTED      → 422 "“xyz” isn't an English word we can teach"                          ← NO AI
      none          → 4
  4 quota           AiUsage.count(learner, today) < 30, else 429; no AI key → 503
  5 insert          WordCard.preparing + form row in one transaction (+ AiUsage.record);
                    unique violation on the form (someone else just did it) → reload, go to 3
  6 SavedWord       NEW, pointing at the PREPARING card → 201 {status: PREPARING}
  7 after commit    CardWriter.write(cardId) on the card executor (2 threads)

CardWriter.write(cardId)
  a call WordCardAi (context: the learner's sentence / passage meaning), timeout 30 s
  b NOT_A_WORD → card.rejected; delete the SavedWords pointing at it
  c parse + CardPolicy; problems → one retry with the problems; still bad → card.failed
  d lock: is there a READY card owning the AI's lemma (or any of its forms)?
        yes → existing.addForms(asked form + new forms), SavedWords.pointTo(existing)
              (a learner who already had it keeps one), delete the PREPARING card
        no  → card.ready(lemma, forms (only forms no other card owns), content)
  (the learner's AiUsage was counted in step 5, when the card was created)
```

`CardRetryJob` (every 2 min): PREPARING cards older than 2 min (the app
restarted mid-call) are written again; after 2 attempts → FAILED. The page
polls `GET /words?ids=…` every 3 s while a word is PREPARING (max 2 min).

---

## 5. REST API

### 5.1 Learner (`/api/v1/vocabulary`, token required unless noted)

```
GET    /lookup?text=           (public)  → card view (READY only) or 404 — never calls AI
POST   /words {text, source?: {exercise, sentence}}
                               → 201 SavedWordView | 200 (already saved) | 422 | 429 | 503
GET    /words?state=&q=&page=&size= → {counts {all,new,learning,mastered}, due, newReady, words[], total}
GET    /words?ids=a,b          → those words (polling while PREPARING)
DELETE /words/{id}             → 204
POST   /words/{id}/retry       → 202 (FAILED card → PREPARING; counts towards the quota)
POST   /sessions {kind: LEARN | REVIEW, exercise?}
                               → 201 {sessionId, kind, words[] (LEARN only: full cards for Meet),
                                      tasks[] (no answers), queue}
POST   /sessions/{id}/answers {taskId, answer}
                               → {correct, expected, why?, explanation, mastered, total, finished,
                                  schedule? {box, from, nextDueOn, changed} (review)}
```

`SavedWordView`: id, word, lemma, pos, level, meaning {en, az}, source,
addedOn, state, box, dueOn, cardStatus. Task views carry the type, prompt
texts, options (choose / pair / match) and a hint (first letter + length),
never the answer. A Listen task must carry the word (the browser speaks it),
so Listen is used only in LEARN sessions, where the learner has just met the
words, and never in REVIEW (decision 7).

### 5.2 Admin (`/api/v1/admin/vocabulary/words`, ADMIN or AUTHOR)

```
GET    /?status=&q=&page=&size= → {cards: [{id, word, level, status, source, saves, lastError, updatedAt}], total}
GET    /{id}                    → {id, version, status, source, revision, saves, lastError, lemma, forms,
                                   pos, level, ipa, meaning {en, az}, examples, collocations, family,
                                   gap {sentence, answer}, pair {sentence, options, answer}, wrongMeanings}
PUT    /{id} (the same shape)   → bad lemma/forms first (422 forms[i]), then CardPolicy (422 with paths);
                                   no version → 400; stale version → 409; a form of another card → 409
POST   /{id}/regenerate         → 202 (PREPARING; the AI rewrites it; learners keep their words; 503 without a key)
DELETE /{id}                    → 204, or 409 "N learners have saved this word"
```

---

## 6. UI

| Route | Template | Script |
|-------|----------|--------|
| `/vocabulary` | `vocabulary.html` (My words) | `js/vocabulary/words.js` |
| `/vocabulary/learn` | `vocabulary-session.html` | `js/vocabulary/session.js` (LEARN) |
| `/vocabulary/review` | `vocabulary-session.html` | `js/vocabulary/session.js` (REVIEW) |
| `/reading/{slug}` | (existing) | `js/reading/app.js` + new `js/vocabulary/save.js` (popover button, selection bar, passage box) |
| `/admin/vocabulary`, `/admin/vocabulary/{id}/edit` | `admin/vocabulary*.html` | `js/admin/vocabulary-*.js` |

Shared: `js/vocabulary/api.js` (calls, `say()` with the browser's speech,
`changed()` for the navbar badge), `js/vocabulary/tasks.js` (the 7 task types
and feedback, like `js/grammar/items.js`), `css/vocabulary.css` (prototype
styles on `app.css` tokens). Navbar: `NavItem(VOCABULARY, "Vocabulary",
"/vocabulary")`; `account-nav.js` fills its badge with the NEW count
(`GET /words?size=1`, signed in; refreshed on `bandup:words-changed`).
Leave guard during a session (`BandUpNav.registerGuard`).

**Reading page (built).** `reading/app.js` only announces what happens:
`bandup:gloss` (a dotted word's popover), `bandup:selection` /
`bandup:selection-end` (text selected, with the Highlight button) and
`bandup:passage` (the passage is drawn). `vocabulary/save.js` listens: it
adds "＋ Add to my words" (or "✓ In my words · Remove") to the popover and a
`#saveWordBtn` next to Highlight for 1–3 selected words; the word's sentence
goes with it. Saved words are marked teal (dotted words by class, other words
wrapped in `span.v-saved`), and `#passageWords` lists the passage's NEW words
with "Learn these words" (`/vocabulary/learn?exercise=…`). Guests see "Sign
in to save words". `GET /words?exercise=` and each word's `forms` serve it.

**My words (built).** Totals; Learn (up to 5 ready) and Daily review cards
(buttons hidden until loaded); add a word (messages for added / already
saved / preparing / errors); tabs and search; rows with meaning, Azerbaijani,
source and day, strength bars, "Preparing… ⏳" (polled every 3 s, 2 min) or
"Couldn't prepare · Try again", and remove; guests see a sign-in card.

**Sessions (built).** LEARN: Goal → Meet (word, 🔊, IPA, tags, meaning,
Azerbaijani, the learner's sentence first, examples with the word in bold,
word pairs, family) → Practise (meaning, match) → Master (a wrong task comes
back; mastery bar) → Done. REVIEW: one pill, "Reviewed n / N", the label is
"from: <passage> · box n" (never the word), feedback "⏱ Next review: … box a →
b" / "stays in box n" / "Mastered". Nothing to do → a message card.

**Admin (built).** `/admin/vocabulary`: search and status filter on the
server, saves, last problem, Edit / Regenerate (ready, failed) / Delete
(nobody saved it). `/admin/vocabulary/{id}/edit`: a form (lemma, forms,
part of speech, level, IPA, meanings, examples, word pairs, family, gap,
word pair with the right option, wrong meanings); 422 errors marked at
their fields; Regenerate (503 without a key); unsaved-changes guard.

---

## 7. Database (Flyway V11)

| Table | Key points |
|-------|------------|
| `vocab_word` | id; `lemma` (unique when not null); `status`; `content` jsonb; `source`; `revision`; `version`; `attempts`; `last_error`; `requested_by` (user, set null on delete); `created_at`, `updated_at` |
| `vocab_word_form` | `form` **primary key** (one card per form, also for REJECTED texts); `word_id` (cascade) |
| `vocab_saved_word` | uuid pk; `user_id` (cascade); `word_id` (restrict); `state`; `box`; `due_on`; `last_answered_on`; `source_exercise_id` (set null); `source_sentence`; `added_on`; unique `(user_id, word_id)`; index `(user_id, due_on)` |
| `vocab_session` | uuid pk; `user_id` (cascade); `kind`; `content` jsonb (snapshots, tasks, queue); `version`; `created_at`; `finished_at`; cleanup after 7 days |
| `vocab_ai_usage` | pk `(user_id, day)`; `count` |

---

## 8. Security

- `/api/v1/vocabulary/lookup` public; everything else under
  `/api/v1/vocabulary/**` needs a token (`VocabularyAccessRules`); admin by the
  shared `/api/v1/admin/**` rule.
- `BANDUP_OPENROUTER_API_KEY` → `bandup.vocabulary.ai.api-key`; empty → AI
  off (503 for new words). Never logged; `AiProperties.toString` hides it.
  Not required by the prod profile (the app runs without it).
- Prompt input only after `WordGate` (≤ 40 chars of letters, `'`, `-`,
  spaces); the context sentence comes from our own passage, not from the
  learner. The answer is parsed as JSON and checked by `CardPolicy`.
- Learners see only their own saved words and sessions (404 otherwise).

## 9. Testing

| Level | What |
|-------|------|
| domain | `WordGate`, `WordNormaliser`, `CardPolicy` (every rule with its path), `WordCard` transitions, `SavedWord` boxes and once-a-day, `TaskBuilder`, `WordSession` queue |
| AI adapter | a stub OpenRouter on the JDK `HttpServer` (no new tooling): request shape (model, key header, prompt with context), OK / NOT_A_WORD / bad JSON / timeout / 401 |
| application + API | **DB first: a known word or form makes 0 AI calls**; a new word makes exactly 1; two learners at the same time → 1 call; lemma joins an existing card; REJECTED never calls again; retry then FAILED; quota 429 (known words don't count); no key → 503; keys absent from every learner response; sessions graded on the server; review boxes |
| browser | reading: popover + selection add; My words with a PREPARING word turning READY; a learn session with a wrong task coming back; review with box changes; phone width; admin edit |

## 10. Decisions

1. **App calls OpenRouter** (user decision): words are saved at any moment,
   so no n8n; the key stays on the server.
2. **DB first, AI once per word** (user decision): forms map to one card;
   every later save is a database read.
3. **Saving needs sign-in**; guests can still see meanings of words already
   in the bank (`/lookup`) — proposed, confirm.
4. **One meaning per card in v1**: the meaning used where the word was first
   saved (or the most common IELTS meaning). A word with two very different
   meanings ("bank") gets a second card later (out of scope) — confirm.
5. **5 words per learn session, no daily cap**; up to 20 per review — confirm.
6. **Leaving a learn session**: words not finished stay NEW (nothing half
   learned is saved).
7. **Listen tasks only in learn sessions**, because the browser must know the
   word to speak it.
8. **Defaults** (settings, change any time): 30 AI cards per learner per day;
   model `bandup.vocabulary.ai.model` = a cheap fast model (to pick with the
   user before task 4).
9. **Word packs later** (prototype screen 5).
10. **Match only for different meanings**: two session words with the same
    meaning text (synonyms) would make the match ambiguous, so the session
    has no match then (found while building the UI; failing test first).
11. **The daily AI limit is counted when the card is created** (not after the
    AI answered), so fast repeated requests can't pass it.
12. **Tests never call the real OpenRouter**: `build.gradle` sets an empty
    key for `test` and `uiTest`; tests that need the AI use `StubOpenRouter`
    (the JDK's HTTP server) and set their own key.
