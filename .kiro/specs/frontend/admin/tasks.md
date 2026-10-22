# Tasks — Frontend · Admin

The admin panel (`templates/admin/`, `static/js/admin/`) switched from its
stub to the admin API (backend design §8.4). **[Frontend]** only. **Nothing
is started.** Each task is done only when requested, one at a time, and ends
green.

- **Depends on:** foundation tasks 1–4 in `../tasks.md` (page shells,
  `auth.js`, sign-in, admin guard).
- **Contract change:** the old UI model (`answerKey` keyed by question
  number, `data-az`, `stageLabel` text, lower-case status) is replaced by
  §8.4: ids on groups and questions, `answer` lists and `explanations` inside
  each question, `data-g` glosses with meanings per language, `version` on
  save, upper-case status.

---

- [ ] 1. Admin API client and the exercise list
  - `api.js`: `createHttpApi` on `apiFetch` becomes the client: list, get,
    create, update, publish, unpublish, delete (§8.4).
  - List page: title, status (Draft / Published / Archived), revision,
    stage + position, question count, last change; archived rows marked and
    not editable.
  - _Verify_: headless Chrome against the real app (requests, paths, bodies); manual:
    the seeded Wolves is listed.
  - _Requirements: backend 4.1_

- [ ] 2. Editor model on the new contract
  - Load `AdminExerciseView`; keep ids of groups and questions; new items
    have no id. Answers live in their question (`answer` is always a list),
    explanations per language. Read-only fields (`n`, `heading`, `surface`,
    `status`, `revision`) are shown, never sent.
  - Serialize back to `ExerciseInput` with the loaded `version`.
  - Metadata fields: title, format, level, timer (none / duration + low-time
    warning), stage + position, topics.
  - _Verify_: headless Chrome: load → serialize unchanged for the seeded Wolves
    (round trip); reordering keeps ids; a new question has none.
  - _Requirements: backend 4.1, 4.8_

- [ ] 3. Passage editor with gloss keys and meanings
  - Marking a word wraps it in `<span class="w" data-g="gN">`, N = the next
    free number in this passage; keys are **never renumbered** (backend
    requirement 5.4). Removing a mark removes its gloss entry.
  - Glosses panel: per key the word (from the HTML), lemma, and a meaning
    per language (az, en, + add an enabled language), with MACHINE /
    reviewed shown; meanings for unmarked keys aren't sent.
  - Evidence helpers keep working (`span.ev[id]`, ids `eN`); preview shows
    what the server will keep (only the allowed tags and attributes).
  - _Verify_: headless Chrome: key allocation (gaps not reused wrongly, no
    renumbering), unmark, serialize glosses; manual: mark, preview, save.
  - _Requirements: backend 5.2–5.4_

- [ ] 4. Every question type in the editor
  - `questionTypes.js` gains SUMMARY_BOX (matching family, gap stem) and
    DIAGRAM_LABEL (shown as "needs a picture — coming with media", not
    selectable until Media exists).
  - Per family: TFNG / YNNG fixed choices; gap answers as a list of accepted
    alternatives + word limit (`maxWords`, `numbersAllowed`); MCQ own options
    + one key; "Choose N" `chooseCount` + own options + N keys; matching
    group bank + one key, `optionsReusable` toggle; SHORT prompt.
  - Instruction + translations per language; explanations per language.
  - _Verify_: headless Chrome per family: editor → `ExerciseInput` shape; client
    hints match the server's publish rules (backend design §8.1).
  - _Requirements: backend 4.2_

- [ ] 5. Save, publish and errors
  - Save draft (create = `POST` with the slug as `id`, then `PUT`); publish;
    unpublish; delete ("archived because learners have attempts" when the
    exercise stays listed as ARCHIVED).
  - `422` → each `errors[].field` (`questionGroups[1].questions[0].answer`,
    `glosses.g1.meanings.xx`, `id`, …) shown at its field, nothing lost;
    errors without a matching field shown at the top.
  - `409` → "Someone else changed this exercise" with Reload (unsaved changes
    offered as a copy).
  - Saving a published exercise: warn that it creates revision N+1; a
    question learners answered can't be removed (the API's 422 message).
  - Unsaved-changes guard keeps working.
  - _Verify_: headless Chrome: error path → field mapping for every path shape;
    409 flow; manual: publish blocked → fix → publish → learner sees it.
  - _Requirements: backend 4.2–4.6, 7_

- [ ] 6. Align client validation with the server
  - `validate.js` becomes hints only (the server decides): same rules and
    messages as `PublishPolicy` and the draft rules, so authors see problems
    before saving.
  - _Verify_: headless Chrome: for each publish rule, client hint and server message
    agree (shared case list).
  - _Requirements: backend 4.2_

- [ ] 7. Remove the public fixture and stub
  - Delete `static/js/admin/fixtures.js` (answer keys, publicly served) and
    the stub client.
  - _Verify_: `GET /js/admin/fixtures.js` → 404; no answer key under
    `static/`; all suites green.
  - _Requirements: backend NFR 1_
