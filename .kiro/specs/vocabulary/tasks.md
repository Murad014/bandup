# Tasks — Vocabulary

Each task ends green (`sh gradlew test`; browser tasks also `uiTest`) and
updates `requirements.md` / `design.md` / this file in the same step. Bug
fixes: failing test first. No code before the user approves this spec; the
user approves each task before the next starts.

---

- [x] 0a. UI prototype approved by the user (`ai-docs/vocabulary/Orbiko — Vocabulary.html`, screens 1–4)
- [x] 0b. Spec approved by the user ("OK all default": decisions 3, 4, 5, 8, 9 as proposed; model `anthropic/claude-haiku-5.5`, setting `BANDUP_VOCABULARY_MODEL`)

- [x] 1. Domain: `word` module
  - `WordCard`, `WordNormaliser`, `WordGate`, `CardPolicy`, value objects,
    `WordCardRepository` (port).
  - _Verify_: unit tests for every card-format rule with its path, the
    input rules, normalising, all card transitions (preparing → ready /
    failed / rejected, retry, edit, forms); ArchUnit green; mutation checks.
  - Done: `WordGateTest`, `CardPolicyTest`, `WordCardTest` (32 tests); 5
    mutations caught (meaning may use the word, gap/pair answer not a form,
    fails one attempt late, asked form dropped).
  - _Requirements: 2.2, 3, 8.2_

- [x] 2. Domain: `saved` and `session` modules
  - `SavedWord` (boxes, once a day, MASTERED), `WordSession`, `TaskBuilder`
    (all 7 task types, shuffles per session), ports.
  - _Verify_: unit tests (learned → box 1; first answer of the day only;
    box 4 → MASTERED; queue: wrong comes back; practise not repeated;
    no Listen in REVIEW; options never in answer position by rule); mutation
    checks.
  - Done: `SavedWordTest`, `TaskBuilderTest`, `WordSessionTest`; 6 mutations
    caught (one survivor — listen in review — led to a test over every box).
  - _Requirements: 5, 6, 7_

- [x] 3. Database `V11__vocabulary.sql` + persistence adapters
  - The 5 tables of design §7; entities, `CardJson`, repository adapters with
    row locks and version checks. Database spec updated.
  - _Verify_: persistence test (round trips; one card per form; unique
    learner+word; delete rules); `SchemaConstraintsTest` table count.
  - Done: `VocabularyPersistenceTest` (6), 40 tables; 3 mutations caught
    (version check, form owned by another card, second card for a taken form).
  - _Requirements: 3, 8_

- [x] 4. AI adapter + saving words
  - `OpenRouterWordCards` (JDK `HttpClient`), `AiProperties`
    (`BANDUP_OPENROUTER_API_KEY`, model, timeout, daily limit), `SaveWord`,
    `CardWriter` (async, after commit), `CardRetryJob`, `AiUsage`,
    `PassageContext`. Model chosen with the user first.
  - _Verify_: stub OpenRouter (JDK `HttpServer`): request shape and key header;
    **a known word or form → 0 AI calls**; a new word → 1; two learners at once
    → 1; lemma joins an existing card; NOT_A_WORD → REJECTED, never called
    again; bad answer → one retry → FAILED; quota (known words don't count);
    no key → 503; restart recovery; the key is never logged.
  - Done: `VocabularyAiTest` (13, with `StubOpenRouter`), `VocabularyWithoutAiTest`,
    `OpenRouterParseTest`; the word endpoints of task 5 were built here
    (save, list/poll, remove, try again, lookup). 4 mutations caught (DB not
    asked first, no limit, no join, retry without the problems).
  - _Requirements: 3, 10_

- [x] 5. Learner API
  - `WordController`, `WordSessionController`, `MyWords`, `WordSessions`,
    `RemoveWord`, `WordLookup`, read model, access rules.
  - _Verify_: API tests for every endpoint; keys absent from all learner
    responses (like `GrammarKeysTest`); other learners' words/sessions 404.
  - Done: `WordSessions`, `WordSessionController`, `SessionViews`;
    `VocabularySessionApiTest` (7: page with totals/tabs/search, remove keeps
    the card, a full learn session with a wrong task coming back and words in
    box 1, one passage's words, nothing to learn/review, review boxes once a
    day and never naming the word, ownership and answer errors; every learner
    response checked for key fields). 3 mutations caught.
  - _Requirements: 4, 5, 6, 7_

- [x] 6. Admin API
  - `AdminWordCardController`, `WordCardAdmin` (list, get, edit with version,
    regenerate, forms, delete).
  - _Verify_: roles; every rule with its path; 409s; regenerate keeps
    learners' words; an edit is seen by learners.
  - Done: `AdminVocabularyApiTest` (6) + regenerate in `VocabularyAiTest`; 2
    mutations caught. Tests never call the real OpenRouter: `build.gradle`
    sets an empty `bandup.vocabulary.ai.api-key` for `test` and `uiTest`.
  - _Requirements: 8_

- [x] 7. UI: My words (`/vocabulary`) + navbar item
  - _Verify_: browser test (totals, tabs, search, add typed word, PREPARING →
    READY with the stub AI, FAILED → Try again, remove; phone width;
    accessibility).
  - Done: `VocabularyPageController`, `vocabulary.html`, `js/vocabulary/api.js`,
    `words.js`, `css/vocabulary.css`, navbar item + badge (`account-nav.js`);
    `VocabularyBrowserTest` (a known word added at once, a new word PREPARING →
    READY through the stub, junk refused, search, remove, the navbar badge,
    phone width; guests asked to sign in); `UiPagesTest`; accessibility.
  - _Requirements: 2, 4, 9_

- [x] 8. UI: reading page — save words
  - Popover button, selection bar, teal marks, "New words from this passage".
  - _Verify_: browser test (dotted word and selected word saved, marks, box,
    guest asked to sign in, highlight still works).
  - Done: events in `reading/app.js`, `#saveWordBtn` and `#passageWords` in
    `reading.html`, `js/vocabulary/save.js`; `/words?exercise=` and `forms`
    in the API; browser test (guest popover, a dotted word saved without AI,
    teal mark, the passage box, a selected word saved with its sentence as
    the AI's context and marked, the learn link).
  - _Requirements: 1, 9_

- [x] 9. UI: learn session
  - _Verify_: browser test (Goal → Meet → Practise → Master → Done, a wrong
    task comes back, words become LEARNING, leave guard; mutation: no
    re-queue fails the test).
  - Done: `vocabulary-session.html`, `session.js`, `tasks.js`; browser test as
    planned; the mutation is caught. Bug found: synonyms made the match
    ambiguous — `TaskBuilder` now skips it (failing test first).
  - _Requirements: 5, 9_

- [x] 10. UI: review
  - _Verify_: browser test (due words via `due_on`, box changes, wrong repeats
    without a second change, never shows the word in labels).
  - Done: browser test (2 due words, "box 2 → 1", "box 2 → 3 · in 1 week",
    "stays in box 1", done with 1 retry); empty review says so.
  - _Requirements: 6, 9_

- [x] 11. Admin pages: Vocabulary
  - _Verify_: browser test (list, search, edit with errors, regenerate,
    delete rules).
  - Done: sidebar item and dashboard card, `admin/vocabulary.html`,
    `admin/vocabulary-edit.html`, `js/admin/vocabulary-api.js`,
    `vocabulary-page.js`, `vocabulary-edit.js`; `AdminVocabularyBrowserTest`
    (list with saves, search, errors at their fields, a fix seen through the
    public lookup, regenerate without a key explained, delete only unsaved);
    accessibility of the list.
  - _Requirements: 8_

- [x] 12. Final pass
  - `backend/design.md` (§4 packages, §9 API, decision), navbar note in
    `reading-practice-ui`, deployment note for `BANDUP_OPENROUTER_API_KEY`
    (for the user's pipeline — the user edits deploy files); full test run.
  - Done: backend design §4, §9 and decision 23; navbar note; the full suite
    and `uiTest` green.

- [x] 13. Recall (Azerbaijani → English) — replaced by task 14 (Sets)
  - Prototype screen 6 approved by the user (no hint, "Recall" name).
  - `SessionKind.RECALL`, `V12__vocabulary_recall.sql` (session kind check),
    `LearnerWords.recallable`, `TaskBuilder` recall tasks, `WordSessions`;
    `/vocabulary/recall`, the My words card, recall mode in `session.js`.
  - _Verify_: unit tests (no hint, synonyms accepted, wrong comes back);
    API test (10 at most, learned words only, one per Azerbaijani meaning,
    boxes and due days unchanged, no answer in the payload, 422 with nothing
    learned); browser test (card, a wrong word comes back, done screen with
    the score and missed words); mutation checks.
  - Done: `TaskBuilderTest` (2), `VocabularySessionApiTest` (3: ten learned
    words, no hint, no answer in the payload, "I don't remember" as
    `answer: null` (`GivenAnswer.DontKnow`), nothing moved, a new mix; one word
    per Azerbaijani meaning, the other accepted; 422 with nothing learned),
    `VocabularyBrowserTest` (card → recall → missed word comes back → "2 / 3"
    with the missed word; boxes unchanged), `UiPagesTest`. 8 mutations caught.
  - _Requirements: 11_

- [x] 14. Sets (replace the daily review and Recall)
  - Prototype screen 7 approved by the user (sets of 25, mastered after 2
    rounds in a row right on the first try, a miss resets, learned words only,
    no refresher).
  - `V13__vocabulary_sets.sql` (`learned_at`, `streak`, `rounds`; kinds LEARN,
    ROUND; old review/recall sessions deleted); `SavedWord` streak instead of
    boxes; `SetPlan`; `WordSession` first tries (`missed`); `TaskBuilder`
    ROUND; `WordSessions` ROUND; `GET /sets`; `/vocabulary/sets`,
    `/vocabulary/round`; My words Sets card and row progress; review and
    recall removed (old links redirect).
  - _Verify_: unit tests (streak, reset on a miss, MASTERED unchanged;
    SetPlan 25 per set, current set, rounds; first try only); API test (only
    the current set, not-mastered words, no hint or answer in the payload,
    first try counts, retry doesn't, mastered after 2 rounds, next set opens,
    422s, sets view); browser test (card → sets page → round with a miss →
    round done); mutation checks.
  - Done: `SavedWordTest` (6), `SetPlanTest` (3), `WordSessionTest` (first
    tries), `TaskBuilderTest` (round tasks), `VocabularyPersistenceTest`;
    `VocabularySessionApiTest` (27 words in 2 sets: only the open set's words,
    no hint, no answer in the payload, a retry doesn't count, mastered after 2
    rounds in a row, round numbers, Set 2 opens, synonyms accepted, both 422s,
    `GET /sets`, old columns untouched); `VocabularyBrowserTest` (Sets card →
    sets page → round 1 with a miss coming back → "2 / 3", right once and
    missed → rounds 2 and 3 → "Set 1 mastered!"; old links land on the sets;
    empty sets); `UiPagesTest` (routes, redirects). Review and recall code,
    `Box` and `ReviewSchedule` removed. 8 mutations caught.
  - _Requirements: 4, 5.6, 5.7, 6, 7_

- [x] 15. Phrases ("vicious cycle" couldn't be added)
  - Failing test first: a phrase card from the stub AI whose gap and word-pair
    blank one word of the phrase → FAILED today.
  - `CardPolicy` phrase blanks (decision 17), prompt phrase rule,
    "Add a word or phrase" in My words.
  - _Verify_: `CardPolicyTest` (phrase blank allowed only when the completed
    sentence has the phrase; one-word cards unchanged), `VocabularyAiTest`
    (phrase → READY); mutation check.
  - Done: the server log wasn't available, so the cause is the likely one,
    reproduced: `CardPolicyTest.aPhraseMayBlankOneOfItsWords…` and
    `VocabularyAiTest.aPhraseGetsACard…` failed first ("The answer must be one
    of the forms"). The rule: one word of a multi-word form, only when the
    blank completes that form. 2 mutations caught (a third was equivalent:
    for one-word cards the rule is unchanged, so the phrase flag was dropped).
  - _Requirements: 2, 3.11_

## Later (not v1)

- Word packs (prototype screen 5), generated in advance.
- Several meanings per word; recorded audio; streaks; vocabulary on "My progress".
