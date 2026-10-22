# Tasks — Frontend · Reading

The learner's reading page (`templates/reading.html`, `static/js/reading/`)
switched from its stub to the learner API (backend design §7.4).
**[Frontend]** only. **Nothing is started.** Each task is done only when
requested, one at a time, and ends green.

- **Depends on:** foundation tasks 1–2 in `../tasks.md` (page shell,
  `auth.js`).
- **Keeps:** the existing behaviour from `reading-practice-ui` (timer,
  highlighting, word list, evidence review, restart, navigation guard).

---

- [ ] 1. Load the passage from the API
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

- [ ] 2. Render every question family
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

- [ ] 3. Submit and show results from the API
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

- [ ] 4. Help language
  - The help language (gloss meanings, translated instructions,
    explanations) is a small picker on the page: default = the signed-in
    learner's support language, else the browser's; the choice is kept per
    browser and sent as `?lang=`.
  - Changing it reloads only the help text (the answers and the timer stay).
  - _Verify_: headless Chrome: picker default order; manual: az → en → az keeps the
    answers.
  - _Requirements: backend 6.1_

- [ ] 5. Remove the public fixture and stub
  - Delete `static/js/reading/fixtures.js` (it holds answer keys and is
    publicly served) and the in-page stub; nothing else replaces them.
  - _Verify_: `GET /js/reading/fixtures.js` → 404; no file under `static/`
    contains an answer key; all suites green.
  - _Requirements: backend NFR 1_
