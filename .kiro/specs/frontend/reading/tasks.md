# Tasks — Frontend · Reading

The learner's reading page (`templates/reading.html`, `static/js/reading/`)
switched from its stub to the learner API (backend design §7.4).
**[Frontend]** only. **All five tasks are done.** Each task is done only when
requested, one at a time, and ends green.

- **Depends on:** foundation tasks 1–2 in `../tasks.md` (page shell,
  `auth.js`).
- **Keeps:** the existing behaviour from `reading-practice-ui` (timer,
  highlighting, word list, evidence review, restart, navigation guard).

---

- [x] 1. Load the passage from the API
  - `api.js`: `httpApi` on `apiFetch` (token optional) becomes the only
    client in the page: `GET /api/v1/passages/{slug}?lang=`.
  - Map the response: glosses from `glosses[el.dataset.g]` (not `data-az`);
    a word with no meaning stays unmarked; stage line built from
    `stageLabel` + `position` ("Stage 1 · Mini passage 4"); timer from
    `durationSeconds` / `lowTimeThresholdSeconds` (none → no timer);
    `direction` on the help text (rtl languages).
  - States: loading; `404` → "This passage isn't available"; network error →
    retry button.
  - _Verify_: headless Chrome against the real API (the seeded Wolves in az
    and en): words, glosses, headings, numbers; 404 and error states.
    Manual: `/reading/yellowstone-wolves`.
  - _Requirements: backend 2, 6_

- [x] 2. Render every question family
  - Today only TFNG and FILL render. Add:
    - MCQ: radio buttons from the question's `options`;
    - "Choose N" (MCMA): checkboxes, at most `chooseCount` ticked, numbered
      "7–8" from `n`/`nTo`;
    - matching (headings, information, features, endings) and SUMMARY_BOX:
      a select per question from the group's `options` bank; when
      `optionsReusable` is false, an option used elsewhere is shown as used;
    - SHORT (prompt + text box), SUMMARY (gap like FILL).
  - Word-limit hint from `maxWords` / `numbersAllowed`
    ("NO MORE THAN TWO WORDS AND/OR A NUMBER").
  - _Verify_: headless Chrome per family (DOM built, answers collected as the API
    expects: text, or a list for "Choose N"); keyboard use of each control.
  - _Requirements: backend 2.4, 3.2_

- [x] 3. Submit and show results from the API
  - `POST /api/v1/attempts?lang=` with `{passageId, elapsedSeconds,
    answers[{n, value}]}`; a signed-in learner's token is sent, so the
    attempt is saved for them.
  - Results from the response: score, total, percent; per question
    correct / partial (`points` of `possible`), "You wrote" from `given`,
    the answer, other accepted answers, the explanation (sanitized HTML),
    "Show in text" via `evidenceId`.
  - `400` (should not happen from this UI) → "Something went wrong with your
    answers", answers kept; network error → retry without losing answers.
  - _Verify_: headless Chrome against the real API (perfect, mixed, "Choose TWO"
    partial); manual: anonymous and signed-in attempts.
  - _Requirements: backend 3_

- [x] 4. Help language
  - The help language (gloss meanings, translated instructions,
    explanations) is a small picker on the page: default = the signed-in
    learner's support language, else the browser's; the choice is kept per
    browser and sent as `?lang=`.
  - Changing it reloads only the help text (the answers and the timer stay).
  - _Verify_: headless Chrome: picker default order; manual: az → en → az keeps the
    answers.
  - _Requirements: backend 6.1_

- [x] 5. Remove the public fixture and stub
  - Delete `static/js/reading/fixtures.js` (it holds answer keys and is
    publicly served) and the in-page stub; nothing else replaces them.
  - _Verify_: `GET /js/reading/fixtures.js` → 404; no file under `static/`
    contains an answer key; all suites green.
  - _Requirements: backend NFR 1_

---

## Done (tasks 1–5, built together)

- `reading/api.js`: learner API through `apiFetch` (token sent when signed
  in). `reading/core.js`: only clock, family map, word-limit hint, numbers,
  help languages — the client-side grader and its helpers are gone.
  `reading/fixtures.js` deleted.
- `reading/app.js`: glosses from `glosses[data-g]` (a word without meaning
  is unmarked: `.w.nogloss`), stage line "Mərhələ 1 · Passage 4" (the view
  has no format, so "Passage", not "Mini passage"), no timer when the
  exercise has none, elapsed time from the start clock; every family (TFNG,
  YNNG, MCQ, "Choose N" with the limit enforced, matching + SUMMARY_BOX with
  the bank and "(used)" marks when options aren't reusable, gap, SHORT,
  DIAGRAM_LABEL as gap); word-limit hints; results with partial points,
  "You answered", other accepted answers, explanation, "Show in text";
  help-language picker (saved per browser → account's support language →
  browser language) that reloads only the help text.
- **Old bug fixed:** "Start again" never worked: the button's `id="reset"`
  shadowed `form.reset()` inside the form, so restart crashed on its first
  line. Now `HTMLFormElement.prototype.reset.call(form)`.
- `SeedDataTest` keeps comparing the seed with the old fixture's glosses
  through test resource `seed/wolves-glosses-from-old-fixture.html`.
- _Verified_: `ReadingBrowserTest` (6, Chrome, real API): Wolves in az
  (title, stage, timer, word list, headings, word limit, popover meaning, no
  answers before submit); switching az → en changes meanings and keeps the
  typed and ticked answers; perfect attempt 6/6 with explanation, evidence
  highlight and a working restart; a published exercise with every family
  (made through the admin API) renders, enforces "Choose TWO", marks used
  headings and is graded by the server 6/8 with the partial and "also
  accepted" lines; a signed-in learner's attempt is stored for them; an
  unknown passage says so; `/js/reading/fixtures.js` → 404. Screenshot in
  `build/screenshots/`. 382 tests green.
