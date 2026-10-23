# Tasks — Frontend · Admin

The admin panel (`templates/admin/`, `static/js/admin/`) switched from its
stub to the admin API (backend design §8.4). **[Frontend]** only. **All seven
tasks are done.** Each task was done when requested and ended green.

- **Depends on:** foundation tasks 1–4 in `../tasks.md` (page shells,
  `auth.js`, sign-in, admin guard).
- **Contract change:** the old UI model (`answerKey` keyed by question
  number, `data-az`, `stageLabel` text, lower-case status) is replaced by
  §8.4: ids on groups and questions, `answer` lists and `explanations` inside
  each question, `data-g` glosses with meanings per language, `version` on
  save, upper-case status.

---

- [x] 1. Admin API client and the exercise list
  - `api.js`: `createHttpApi` on `apiFetch` becomes the client: list, get,
    create, update, publish, unpublish, delete (§8.4).
  - List page: title, status (Draft / Published / Archived), revision,
    stage + position, question count, last change; archived rows marked and
    not editable.
  - _Verify_: headless Chrome against the real app (requests, paths, bodies); manual:
    the seeded Wolves is listed.
  - _Requirements: backend 4.1_

- [x] 2. Editor model on the new contract
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

- [x] 3. Passage editor with gloss keys and meanings
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

- [x] 4. Every question type in the editor
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

- [x] 5. Save, publish and errors
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
  - A `401` that sends the author to sign in keeps the unsaved editor content
    (`sessionStorage`) and offers it back afterwards (moved from foundation
    task 4).
  - _Verify_: headless Chrome: error path → field mapping for every path shape;
    409 flow; manual: publish blocked → fix → publish → learner sees it.
  - _Requirements: backend 4.2–4.6, 7_

- [x] 6. Align client validation with the server
  - `validate.js` becomes hints only (the server decides): same rules and
    messages as `PublishPolicy` and the draft rules, so authors see problems
    before saving.
  - _Verify_: headless Chrome: for each publish rule, client hint and server message
    agree (shared case list).
  - _Requirements: backend 4.2_

- [x] 7. Remove the public fixture and stub
  - Delete `static/js/admin/fixtures.js` (answer keys, publicly served) and
    the stub client.
  - _Verify_: `GET /js/admin/fixtures.js` → 404; no answer key under
    `static/`; all suites green.
  - _Requirements: backend NFR 1_

---

## Done (tasks 1–7, built together)

- `admin/api.js`: the §8.4 API through `apiFetch`; `admin/fixtures.js` and
  the stub deleted.
- `admin/editor.js` rewritten on the `AdminExerciseView` model: ids kept,
  answers (lists) and explanations per language inside each question,
  `toInput()` sends `ExerciseInput` (no read-only fields, loaded `version`).
  Details: address (new only), title, format, level, timer, stage +
  position, topic ids. Word meanings panel per gloss key (surface from the
  HTML, lemma, az / en meanings, "machine-written, not reviewed" note).
  Groups: all types (DIAGRAM_LABEL listed but disabled until media),
  instruction + translations, "Choose N", word limit + numbers, reusable
  options, banks; questions: stem per type, options, answer editor per
  family, evidence (datalist), explanations; move up/down keeps ids.
- Save / publish / unpublish; a new exercise moves to its `/edit` URL after
  the first save; 422 → each error at its `data-field` (= the API path) and
  listed on top; 409 → "someone else changed it" with Reload; saving a
  published exercise asks first ("creates revision N+1"); archived →
  read-only with an explanation.
- Signed out mid-edit: `guard.js` doesn't leave a page with unsaved work
  (`setUnsavedCheck`); the save's 401 keeps the work in `sessionStorage`,
  goes to sign-in and offers "Restore them" afterwards.
- `admin/validate.js`: publish hints only, same rules and messages as the
  server's `PublishPolicy`, shown live in "Before publishing".
- `admin/passage.js`: `data-g` keys = highest key + 1, never renumbered;
  preview keeps exactly what the server's sanitizer keeps.
- `questionTypes.js`: 13 types (SUMMARY_BOX added; DIAGRAM_LABEL disabled).
- List page: Exercises with stage + position, status (+ revision), last
  change; archived rows have no actions; deleting an exercise with attempts
  says it was archived; publish errors from the list point to the editor.
- _Verified_: `AdminBrowserTest` (8, Chrome, real API): list from the API
  (+ fixture gone); Wolves editor fully loaded (25 meanings, machine notes,
  answers, ids); an author writes a new exercise in the UI (address, title,
  passage, gloss + az meaning, TFNG question, answer, evidence), hints
  update, saves (URL, version 0), publishes, and a learner opens it with the
  meaning; publish blocked → error at the answer field and on top, still
  draft; two authors → the second gets the 409 notice, nothing lost;
  published save asks and makes revision 2; delete with attempts →
  archived row with the note; signed out in another tab → stays, save → sign
  in → "Restore them" brings the work back. Screenshots in
  `build/screenshots/`.
