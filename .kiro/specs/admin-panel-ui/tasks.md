# Tasks — Admin Panel UI

Incremental, testable plan for the **admin UI side**, built against a **stub
admin API** so work can proceed before the backend exists. Each task lists the
requirements it satisfies.

Conventions:
- The UI consumes the §4 admin API contract from `design.md`.
- Author-supplied HTML is sanitized **server-side** on save; the client never
  relies on its own sanitization for safety.
- Admin objects include answers; the learner read API never does.
- Note (later): the Node test files (`jstest/`) were removed; the app needs
  no Node. Frontend checks are MockMvc + headless Chrome (`frontend/tasks.md`).
- "Verify" = an automated test (Node+jsdom for client logic, MockMvc for
  server routing/authorization) and/or a manual browser check.

---

- [x] 1. Admin layout (sidebar) + access-control stand-in
  - Create a dedicated admin layout (`templates/admin/layout.html`) with a
    persistent left sidebar (brand/area title, data-driven nav items Dashboard /
    Sessions, active-item marking, sign-out/exit affordance) and a content slot;
    reuse `app.css` theme vars + a new `admin.css`. Add `admin/sidebar.js` for the
    responsive off-canvas toggle (keyboard-operable) and the nav-guard hook.
  - Add admin Thymeleaf routes (`/admin`, `/admin/sessions`,
    `/admin/sessions/new`, `/admin/sessions/{id}/edit`) decorating the admin
    layout with the correct `activeKey`.
  - Add a role-gated **Admin** entry on the public navbar (entry point only) and
    a server-side **deny-by-default** authorization layer for `/admin/**` and
    `/api/v1/admin/**` (stands in until the auth spec lands).
  - _Verify_: MockMvc — admin routes/API denied without the admin role (not just
    hidden); allowed with it; admin page renders the sidebar with the active item
    marked (`aria-current`).
  - _Requirements: 1, 11, NFR 1, NFR 4_

- [x] 2. Stub admin API client + fixtures
  - Implement `admin/api.js`: `list`, `get`, `create`, `update`, `remove`,
    `publish`, `unpublish` against the §4 contract; stub + `httpApi` (mirrors
    `reading/api.js`).
  - Add `admin/fixtures.js` seed sessions as **full objects (with answers)**;
    stub `get` returns the full object, list returns summaries.
  - _Verify_: Node tests — CRUD round-trips on the stub; `get` includes
    `answerKey`; unknown id → 404; create assigns an id.
  - _Requirements: 2, 3, 6 (contract)_

- [x] 3. Sessions list page
  - Implement `admin/sessions.js`: render rows (title, stage, status, question
    count) from `list`; empty state + "New session"; row actions edit / delete /
    publish-unpublish; error + retry.
  - Delete requires a confirmation naming the session (Req 9.1).
  - _Verify_: jsdom — rows render; empty state shows; delete confirmation gates
    the API call and removes the row on success; publish toggles status; error
    path shows retry.
  - _Requirements: 2, 8.3, 8.4, 9_

- [x] 4. Client validation module
  - Implement `admin/validate.js` (pure) covering §6 rules: required
    title/stage; duration/threshold; unique contiguous numbering; answer
    presence/format (TFNG set, FILL non-empty list); evidence-reference
    integrity; unreferenced-evidence warning; draft-vs-publish strictness.
  - _Verify_: Node unit tests for each rule, including draft relaxations vs.
    publish enforcement.
  - _Requirements: 7, 8.1, 8.2_

- [x] 5. Session metadata editor
  - Implement the editor shell (`admin/editor.js`) + `session-edit.html`: load
    via `get` (or blank for new), bind metadata fields (title, stage, duration
    mm:ss/seconds, threshold), save via `create`/`update`.
  - Show field-level validation (client) and server `422` errors without losing
    input.
  - _Verify_: jsdom — fields populate from a fixture; invalid metadata blocks
    save with messages; successful save calls the right endpoint; server-error
    payload renders field messages and preserves input.
  - _Requirements: 3, 7.2, 7.4_

- [x] 6. Passage editor — gloss/evidence helpers + serialization
  - Implement `admin/passage.js`: the editing surface (contenteditable per
    design §5.2) with toolbar helpers **Add gloss** (wrap selection in
    `span.w[data-az]`, prompt for AZ), **Mark evidence** (wrap in `span.ev[id]`
    with next free `eN`), **Remove mark** (unwrap, preserve text); serialize to
    `passageHtml` keeping only allowed markup; maintain the evidence-id registry.
  - _Verify_: jsdom — add gloss wraps with `data-az`; mark evidence allocates a
    unique `eN`; remove unwraps preserving text; serialization yields only
    allowed tags/attrs; registry reflects current `.ev` ids.
  - _Requirements: 4_

- [x] 7. Passage live preview
  - Render the serialized `passageHtml` in a read-only preview using the learner
    reading styles (`.w` glosses, `.ev` evidence); allow toggling evidence
    visualisation.
  - _Verify_: jsdom — preview reflects edits; gloss count and evidence spans in
    the preview match the serialized passage.
  - _Requirements: 4.5, 4.6_

- [x] 8. Question groups + questions editor
  - In `editor.js`: add/reorder/remove groups (type TFNG/FILL, heading,
    instruction); add/reorder/remove questions (TFNG statement; FILL pre/post);
    maintain unique contiguous numbering; renumber on change.
  - _Verify_: jsdom — adding a TFNG and a FILL group yields the right controls;
    reorder/remove renumbers; numbering stays contiguous across groups.
  - _Requirements: 5_

- [x] 9. Answers, explanations, evidence links
  - Per question: TFNG answer selector (TRUE/FALSE/NOT GIVEN); FILL accepted
    answers (one or more); explanation editor (HTML fragment); evidence picker
    populated from the passage registry (or unset).
  - Wire validation so missing/invalid answers and dangling evidence refs are
    flagged (Req 6.5, 7).
  - _Verify_: jsdom — answer controls bind per type; evidence picker lists
    current `eN` ids; a reference to a removed evidence id is flagged.
  - _Requirements: 6, 7.1_

- [x] 10. Draft / publish lifecycle
  - Wire Save (draft — relaxed validation) and Publish (full validation via
    `validate.js`, then `publish`); Unpublish sets back to draft; reflect status
    in editor + list.
  - _Verify_: jsdom — draft save allows incomplete content; publish blocks on
    invalid content and succeeds when valid; unpublish returns to draft.
  - _Requirements: 7, 8_

- [x] 11. Unsaved-changes guard
  - Mark the editor dirty on any change; register the shared nav guard
    (`window.BandUpNav.registerGuard`) + a `beforeunload` handler; clear on
    successful save.
  - _Verify_: jsdom — editing sets dirty and registers the guard; save clears it;
    guard returns false after save.
  - _Requirements: 10_

- [x] 12. Accessibility and responsive pass
  - Labelled controls for metadata/questions/answers; keyboard-operable editor
    toolbar, evidence picker, and **sidebar toggle**; focus indicators;
    current-page indication in the sidebar; responsive two-pane
    (editor/preview) collapsing on narrow screens and the **sidebar collapsing
    off-canvas**; dark/light theming via shared vars.
  - _Verify_: keyboard-only walkthrough of create→author→publish including
    sidebar navigation; layout check at narrow/wide widths; theme check.
  - _Requirements: 11, NFR 4_

- [x] 13. End-to-end authoring flow against the stub
  - Wire all modules: list → new → metadata → passage (glosses + evidence) →
    questions → answers/explanations/evidence → validate → publish → appears
    published in the list; round-trip edit.
  - Confirm the produced `passageHtml` + evidence ids line up with what the
    learner Reading UI renders (cross-check against `reading-practice-ui`).
  - Remove the stub default once the real admin API + sanitizer exist (handoff to
    the backend spec).
  - _Verify_: Node+jsdom happy-path covering the full authoring loop; manual
    click-through.
  - _Requirements: 1–10 (integration)_

---

## Resolve before/while implementing (from design §10)

- Passage editor surface: contenteditable (recommended) vs. textarea+helpers
  (Task 6).
- Evidence-id allocation and delete behaviour (block vs. cascade-unset)
  (Tasks 6, 9).
- Slug/id generation (server vs. author) (Tasks 2, 5).
- Auth model is a separate spec; this UI assumes an admin role + deny-by-default
  guard (Task 1).
- FILL accepted-answer matching must match `reading/core.js` normalization
  (Tasks 4, 9).

## Cross-spec dependency

The answer-key integrity and HTML sanitization this UI assumes are enforced by
the **backend spec** (persistence, server-side sanitizer per reading §9,
authorization, and the admin endpoints). The admin UI is built against the stub
until that lands; flipping `USE_STUB` off switches to the real API.
