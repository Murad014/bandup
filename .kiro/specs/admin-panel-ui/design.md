# Design — Admin Panel UI

## 1. Context and goals

The Admin Panel UI lets authors manage Reading sessions end-to-end: metadata,
passage HTML (with gloss/evidence helpers and preview), questions, answers,
explanations, and evidence links. It follows the same **hybrid architecture** as
the reading UI:

- **Thymeleaf** serves the admin pages (routing, shell, server-rendered chrome).
- A **REST API** under `/api/v1/admin/**` carries all data and logic.
- The pages' **JavaScript consumes that API** via `fetch`, built against a stub
  first so the UI can progress before the backend exists.

Two concerns are first-class here (they were deferrable for the public reading
screen, they are not here):

- **Access control** — the admin area is privileged and write-capable.
- **Server-side HTML sanitization** — authors supply HTML; the server is the
  trust boundary.

### Goals

- Reuse the shared app shell/navbar and theming.
- Make authoring glosses/evidence approachable (no raw HTML required), while
  still producing the exact `passageHtml` the learner UI renders.
- Keep the admin client a thin consumer of the admin REST API.
- Produce content that round-trips: what an author builds here is what the
  learner sees, and the evidence links line up.

---

## 2. High-level architecture

```
Browser (author)
  │ 1. GET /admin, /admin/sessions, /admin/sessions/{id}/edit   (navigation)
  ▼
┌───────────────────────────────┐
│ Thymeleaf @Controller (admin) │ returns admin pages using a dedicated admin
│   AdminPageController         │ layout (persistent left sidebar); behind an
│                               │ authorization layer. No content/answers in HTML.
└───────────────────────────────┘
  │ 2. pages load admin JS modules
  ▼
┌───────────────────────────────┐
│ admin JS (ES modules)         │ 3. GET/POST/PUT/DELETE /api/v1/admin/passages
│  - sidebar (toggle, active)   │ 4. passage editor (gloss/evidence helpers)
│  - sessions list              │ 5. question/answer/evidence editors
│  - session editor + preview   │
└───────────────────────────────┘
  │
  ▼
┌───────────────────────────────┐
│ Admin REST API (@RestController, secured)                            │
│   AdminPassageApiController → full objects WITH answers; sanitizes   │
│   author HTML on write; validates; manages draft/published status.  │
└───────────────────────────────┘
        │ shared data store │
        ▼                   ▼
   learner read API    admin write API
   (no answers)        (full objects)
```

The public top navbar only offers a role-gated **Admin** link to enter the
area; once inside, navigation is the admin sidebar. Admin pages carry only
routing/ids; all content is fetched. Grading/answer data lives behind admin
endpoints only.

---

## 3. Admin layout and sidebar navigation

The admin area uses a **dedicated admin layout** with a **persistent left
sidebar**, not the public top navbar (Requirement 11).

- A dedicated `templates/admin/layout.html` provides the admin shell: `<html>`,
  `<head>` (reusing `app.css` theme variables + `admin.css`), a **sidebar**
  region, and a main content area where each admin page injects its body. It
  does not render the public navbar.
- The **public** top navbar (from `reading-practice-ui`) keeps only a role-gated
  **Admin** link as the entry point into the area; all in-admin navigation is
  the sidebar.

### Sidebar

- Rendered from a **single data-driven list** of admin nav items supplied by the
  controller/model — adding a new section (e.g. a future Grammar content type)
  is a data change, not a per-page edit (Requirement 11.3).

  ```
  AdminNavItem { key, label, href, icon? }
  ```

- Contents: the admin brand/area title at the top; the nav items (e.g.
  **Dashboard**, **Sessions**); and a sign-out / exit-to-app affordance pinned at
  the bottom (Requirement 11.5).
- The item whose `key` matches the page's `activeKey` is marked current
  (`aria-current="page"`) (Requirement 11.2).
- **Responsive** (Requirement 11.4): on wide screens the sidebar is pinned
  beside the content; on narrow screens it collapses to an off-canvas drawer
  opened by a keyboard-operable toggle (`aria-expanded`).
- **Theming/a11y** (Requirement 11.6): uses the shared theme variables and
  focus-visible rules; the active item and toggle are keyboard operable.
- The unsaved-changes guard (§5.3 / Requirement 10) hooks sidebar link clicks the
  same way the shared nav guard does, so navigating away from a dirty editor
  prompts for confirmation.

### Routes (Thymeleaf)

| Route | View | Purpose |
|------|------|---------|
| `GET /admin` | `admin/index` | admin dashboard (sidebar: Dashboard active) |
| `GET /admin/sessions` | `admin/sessions` | list sessions (Sessions active) |
| `GET /admin/sessions/new` | `admin/session-edit` | create |
| `GET /admin/sessions/{id}/edit` | `admin/session-edit` | edit |

All admin views decorate `admin/layout.html` and sit behind the authorization
layer (§7).

---

## 4. Admin REST API contract (consumed by the UI)

The admin objects are the **full** editable form, including answers — the key
difference from the learner read API. The server spec defines these
authoritatively; this is the client's view.

### 4.1 `GET /api/v1/admin/passages` — list

```json
[
  { "id": "yellowstone-wolves", "title": "The Return of the Wolves",
    "stageLabel": "Stage 1 · Mini passage 4", "status": "published",
    "questionCount": 6, "updatedAt": "2026-10-20T12:00:00Z" }
]
```

### 4.2 `GET /api/v1/admin/passages/{id}` — full editable object

```json
{
  "id": "yellowstone-wolves",
  "status": "draft",
  "title": "The Return of the Wolves",
  "stageLabel": "Stage 1 · Mini passage 4",
  "durationSeconds": 300,
  "lowTimeThresholdSeconds": 60,
  "passageHtml": "<span class=\"ev\" id=\"e1\">By the 1920s … <span class=\"w\" data-az=\"məhv edilmək\">eliminated</span> …</span>.",
  "questionGroups": [
    { "type": "TFNG", "heading": "Questions 1–3", "instruction": "Do the statements agree …",
      "questions": [ { "n": 1, "type": "TFNG", "text": "Wolves disappeared … disease." } ] },
    { "type": "FILL", "heading": "Questions 4–6", "instruction": "Complete the sentences …",
      "questions": [ { "n": 4, "type": "FILL", "pre": "Without wolves … near", "post": "." } ] }
  ],
  "answerKey": [
    { "n": 1, "answer": "FALSE", "evidenceId": "e1", "explanation": "<b>Mainly</b> = <i>largely</i> …" },
    { "n": 4, "answer": ["riverbanks"], "evidenceId": "e4", "explanation": "…" }
  ]
}
```

- `passageHtml`, `explanation`, and `instruction` are the sanitized stored HTML
  (the server sanitizes on write; the response is already safe to preview).
- `answer` is a string for TFNG, an array of accepted values for FILL.

### 4.3 Write endpoints

| Method | Path | Body | Result |
|--------|------|------|--------|
| `POST` | `/api/v1/admin/passages` | full object (no id) | created object (with id) |
| `PUT` | `/api/v1/admin/passages/{id}` | full object | updated object |
| `DELETE` | `/api/v1/admin/passages/{id}` | – | `204` |
| `POST` | `/api/v1/admin/passages/{id}/publish` | – | object with `status:"published"` |
| `POST` | `/api/v1/admin/passages/{id}/unpublish` | – | object with `status:"draft"` |

On write the server **sanitizes** the HTML fields and **validates** (§6). On
validation failure it returns `422` with a field-error payload:

```json
{ "errors": [ { "field": "answerKey[2].answer", "message": "Answer is required" },
              { "field": "questions[5].evidenceId", "message": "No such evidence id 'e9'" } ] }
```

The UI renders these without discarding the author's input (Requirement 7.4).

---

## 5. Client structure and the passage editor

### 5.1 Modules (ES modules under `static/js/admin/`)

```
admin/
├── api.js        list/get/create/update/delete/publish; stub + httpApi (mirrors reading/api.js)
├── fixtures.js   stub seed sessions (full objects incl. answers) for dev
├── sidebar.js    sidebar toggle (off-canvas on narrow) + active item + nav guard hook
├── sessions.js   list page controller (render rows, delete, publish toggle)
├── editor.js     session editor controller (metadata, groups, questions, dirty guard, save/publish)
├── passage.js    passage editor: gloss/evidence helpers + live preview
└── validate.js   client-side pre-save validation (mirrors server rules in §6)
```

The passage preview reuses the learner rendering approach: inject the authored
`passageHtml` and style `.w`/`.ev` with the shared reading styles, so the author
sees exactly what the learner will.

### 5.2 Passage editor design (Requirement 4)

The hard part is letting authors produce `passageHtml` with `.w`/`.ev` markup
without hand-writing HTML. Design:

- **Editing surface**: a `contenteditable` passage region (rich enough to select
  text and apply marks) OR a plain HTML `<textarea>` with helper buttons. The
  recommended v1 is a `contenteditable` region because it lets authors select a
  run of text and click a helper, mirroring the learner's own
  select-to-highlight interaction.
- **Toolbar helpers**:
  - **Add gloss** — wraps the current selection in `<span class="w" data-az>`;
    prompts for the Azerbaijani meaning and stores it in `data-az`.
  - **Mark evidence** — wraps the current selection in
    `<span class="ev" id="eN">`, allocating the next free `eN` id.
  - **Remove mark** — unwraps the gloss/evidence span under the cursor,
    preserving text (same unwrap logic as the learner highlight remove).
- **Serialization**: on change, the editor serializes its DOM to the
  `passageHtml` string, keeping only the allowed tags/attributes (`span.w[data-az]`,
  `span.ev[id]`, basic inline emphasis, paragraph breaks). This client-side
  normalization is for authoring convenience only — the **server re-sanitizes**
  on save (§7); the UI never trusts its own output for safety.
- **Evidence registry**: the editor tracks the set of `eN` ids currently present,
  exposed to the question editor's evidence picker (Requirement 6.4) and to
  validation (unreferenced/missing ids, §6).
- **Live preview**: a read-only pane renders the serialized `passageHtml` using
  the learner reading styles; evidence spans can be toggled to visualise them.

### 5.3 Editor state and dirty tracking (Requirement 10)

- The editor holds the working session object in memory; any field/passage/
  question change marks `dirty = true`.
- The dirty flag drives the shared navigation guard
  (`window.BandUpNav.registerGuard`) and a `beforeunload` handler, reusing the
  same mechanism the reading session uses.
- A successful save/publish clears `dirty`.

---

## 6. Data and validation model

Working shape held by the editor (superset of the learner session):

```
AdminSession {
  id?, status: 'draft'|'published',
  title, stageLabel, durationSeconds, lowTimeThresholdSeconds,
  passageHtml,
  questionGroups: [ { type:'TFNG'|'FILL', heading, instruction, questions:[Question] } ],
  answerKey: [ { n, answer, evidenceId?, explanation } ]
}
Question (TFNG) { n, type:'TFNG', text }
Question (FILL) { n, type:'FILL', pre, post }
```

Validation rules (enforced client-side in `validate.js` and authoritatively on
the server; both share this list):

1. `title`, `stageLabel` non-empty.
2. `durationSeconds` > 0; `0 < lowTimeThresholdSeconds ≤ durationSeconds`.
3. Question numbers are unique and contiguous (1..N across groups in order).
4. Every question has an answer-key entry; TFNG answer ∈ {TRUE, FALSE, NOT
   GIVEN}; FILL answer is a non-empty list of non-empty strings.
5. Every `evidenceId` referenced by the answer key exists among the passage's
   `.ev` ids.
6. Warning (non-blocking): evidence ids present in the passage but referenced by
   no question.

Draft save relaxes rules 3–5 (allows incomplete work); **publish** enforces all
(Requirement 8.2).

---

## 7. Security

- **Authorization layer (mandatory, Requirement 1 / NFR 1).** Both the admin
  Thymeleaf routes (`/admin/**`) and the admin API (`/api/v1/admin/**`) are
  protected server-side. Pages redirect unauthenticated users to sign-in; the
  API returns `401`/`403`. This layer exists independently of the navbar's
  Admin-link visibility, so turning on real authentication later cannot
  retroactively expose the area. Until the auth spec lands, a deny-by-default
  guard (e.g. a filter/interceptor gated by a role flag) stands in.
- **Server-side sanitization (NFR 2).** On every write, the server sanitizes
  `passageHtml`, each `explanation`, and each `instruction` against the strict
  allow-list from `reading-practice-ui` design §9 (permit `p, span, mark, em,
  strong, b, i, br`; attributes `class, id, data-az` on `span`; strip scripts,
  handlers, styles, other tags/attrs). The sanitized form is what gets stored and
  later served to learners.
- **No answer leakage (NFR 3).** Answers/explanations are returned only by admin
  endpoints. The learner `GET /api/v1/passages/{id}` continues to omit them.
- **CSRF**: admin write requests include the server's CSRF token (standard for
  authenticated, cookie-based sessions). The stub ignores it; the real client
  attaches it.

---

## 8. Testing strategy (UI side)

- **Validation unit tests** (`validate.js`, pure): numbering, answer formats,
  evidence-reference integrity, duration/threshold, draft-vs-publish strictness.
- **Stub API tests**: list/get/create/update/delete/publish round-trips;
  admin `GET` returns answers; validation errors surface as `422`-shaped payloads.
- **Passage editor tests (jsdom)**: add gloss wraps selection with `data-az`;
  mark evidence allocates a unique `eN`; remove unwraps preserving text;
  serialization yields only allowed markup; evidence registry updates; preview
  renders `.w`/`.ev`.
- **Editor flow tests (jsdom)**: dirty tracking set on edit and cleared on save;
  nav guard registered while dirty; add/remove/reorder questions renumbers and
  updates evidence references; publish blocked on invalid content with
  field-anchored errors; server `422` errors shown without losing input.
- **List page tests (jsdom)**: rows render; delete requires confirmation and
  removes on success; publish/unpublish toggles status.
- **Access-control test (server, MockMvc)**: `/admin/**` and `/api/v1/admin/**`
  are denied without the admin role (deny-by-default), independent of navbar
  links.

---

## 9. File layout (UI side)

```
src/main/resources/
├── templates/
│   └── admin/
│       ├── layout.html         dedicated admin shell (sidebar + content slot)
│       ├── index.html          admin dashboard (decorates admin/layout.html)
│       ├── sessions.html       sessions list
│       └── session-edit.html   create/edit editor
└── static/
    ├── css/admin.css           admin layout + sidebar styles (builds on app.css vars)
    └── js/admin/
        ├── api.js  fixtures.js  validate.js  sidebar.js
        ├── sessions.js  editor.js  passage.js
```

Java (names indicative; controllers served by the backend spec):
`AdminPageController` (Thymeleaf), `AdminPassageApiController` (REST),
an authorization layer, and the HTML sanitizer.

---

## 10. Open questions

1. **Passage editor surface** — `contenteditable` (recommended) vs. raw HTML
   `<textarea>` + helpers. Affects §5.2 and editor tests. Richer editors add
   complexity; v1 can start minimal.
2. **Evidence id scheme** — sequential `eN` allocation and what happens to
   references when an evidence span is deleted (block delete vs. cascade-unset).
3. **Slug/id generation** — server-generated from title vs. author-supplied,
   and uniqueness handling.
4. **Auth model** — roles/claims shape and the sign-in flow (separate auth spec);
   this spec only assumes an admin role and a deny-by-default guard.
5. **Multiple accepted FILL answers & normalization** — confirm matching rules
   align with the learner grader (`reading/core.js`).
6. **Reordering UX** — drag-and-drop vs. move up/down buttons for groups and
   questions.
