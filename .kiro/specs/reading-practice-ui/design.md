# Design — Reading Practice UI

## 1. Context and goals

This design covers the **UI side** of the Reading Practice feature within the
agreed **hybrid architecture**:

- **Thymeleaf** serves the page (HTML shell, layout, routing).
- A **REST API** (`/api/v1/**`) carries all business logic and data; it is the
  future public API.
- The page's **JavaScript consumes that same REST API** via `fetch`.
- **No answer key lives in the browser.** The passage endpoint returns questions
  without answers; grading happens on the server.

The design intentionally mirrors the proven interactions in
`ai-docs/prototype/index.html`, while relocating the answer key and grading to
the server.

### Design goals

- Keep the prototype's look and interactions intact.
- Make the client a thin consumer of the REST API.
- Keep the DOM structure driven by data returned from the API.
- Preserve accessibility, responsiveness, and theming from the prototype.

---

## 2. High-level architecture

```
Browser
  │  1. GET /reading/{id}            (navigation)
  ▼
┌─────────────────────────────┐
│ Thymeleaf @Controller       │  returns the page shell (no answers, no
│   ReadingPageController     │  question data embedded)
└─────────────────────────────┘
  │  2. page loads reading.js
  ▼
┌─────────────────────────────┐
│ reading.js (UI controller)  │
│  - fetch session            │  3. GET  /api/v1/passages/{id}
│  - render passage+questions │  4. POST /api/v1/attempts
│  - timer / highlight / gloss│
│  - render feedback          │
└─────────────────────────────┘
  │
  ▼
┌─────────────────────────────┐
│ REST API @RestController    │  JSON; shared service layer holds grading +
│   PassageApiController      │  answer key (server-side only)
│   AttemptApiController      │
└─────────────────────────────┘
```

The Thymeleaf page carries only the session id and static chrome. Everything
dynamic is fetched. This keeps the same code path the future public SPA/mobile
client would use.

---

## 3. Page delivery (Thymeleaf layer)

### Route

- `GET /reading/{id}` → `ReadingPageController` returns `reading` view.

### View responsibilities

- Compose from a **shared app-shell layout** (see §3a) that provides the navbar
  and page chrome; the reading view supplies only the feature content fragment.
- Render the static reading shell: header bar (title placeholder, timer slot,
  submit button), the two-panel `main` layout, the floating highlight button and
  popover elements.
- Embed only the **session id** (e.g. as a `data-session-id` attribute on a root
  element) and the API base path. No passage text, no questions, no answers.
- Link the stylesheet (`/css/reading.css`) and the module script
  (`/js/reading.js`). Shell-wide styles come from `/css/app.css`.

The existing prototype CSS moves to `src/main/resources/static/css/reading.css`
essentially unchanged. The inline `<script>` becomes
`src/main/resources/static/js/reading.js`, refactored to fetch data instead of
using the hardcoded `Q` array.

> Rationale: Thymeleaf gives us server-rendered routing and a place to inject
> per-session config, but the content stays API-driven so the browser code is
> identical to what a public client would run.

---

## 3a. App shell and navigation (Req 10)

The navbar is a cross-cutting concern, so it lives in a **shared Thymeleaf
layout** that every feature page reuses — not inside the reading page.

### Structure

- `templates/layout/main.html` — the app shell: `<html>`, `<head>` (common
  meta/CSS), a `navbar` fragment, and a content slot where each feature injects
  its page body. Implemented with Thymeleaf layout fragments
  (`th:fragment` / `th:insert`, or the layout-dialect `layout:decorate`).
- `templates/fragments/navbar.html` — the navbar fragment, rendered from a
  **single data-driven list** of nav items supplied by the controller/model.

### Nav item model (server-side)

```
NavItem { key, label, href, enabled, comingSoon }
```

Example list driving the navbar:

```
[ { key:"reading", label:"Reading", href:"/reading/…", enabled:true  },
  { key:"grammar", label:"Grammar", href:"#",          enabled:false, comingSoon:true } ]
```

- The controller passes this list plus the `activeKey` for the current page.
- Adding **Grammar** later = flip `enabled:true` and point `href` at the grammar
  route; no change to feature pages (Req 10.4).
- Disabled/`comingSoon` items render as non-interactive with a "soon" badge
  (Req 10.3).
- The fragment marks the item matching `activeKey` as current
  (`aria-current="page"`) (Req 10.2, 10.7).

### Behaviour

- **Responsive** (Req 10.5): the navbar collapses to a toggle menu under the
  layout breakpoint; the toggle is keyboard-operable with proper
  `aria-expanded` state.
- **Unsaved-progress guard** (Req 10.6): while a reading session is in progress
  and not yet submitted, navbar links intercept navigation and ask the learner
  to confirm leaving (the client exposes an "in progress" flag the shell checks;
  see §6.9). Submitting or restarting clears the flag.
- **Theming/a11y** (Req 10.7): the navbar uses the shared theme variables and
  focus-visible rules from the app stylesheet.

> This shell is the reuse point for all future features. The reading feature is
> one content fragment inside it; Grammar will be another.

---

## 4. API contract consumed by the UI

The UI depends on two endpoints. These are the client's view of the contract;
the server spec defines them authoritatively.

### 4.1 `GET /api/v1/passages/{id}` — load session

Response (no answers, no explanations):

```json
{
  "id": "yellowstone-wolves",
  "stageLabel": "Stage 1 · Mini passage 4",
  "title": "The Return of the Wolves",
  "durationSeconds": 300,
  "lowTimeThresholdSeconds": 60,
  "passageHtml": "<span class=\"ev\" id=\"e1\">By the 1920s, grey wolves had been completely <span class=\"w\" data-az=\"məhv edilmək\">eliminated</span> from Yellowstone...</span>",
  "questionGroups": [
    {
      "type": "TFNG",
      "heading": "Questions 1–3",
      "instruction": "Do the statements agree with the information in the text?...",
      "questions": [
        { "n": 1, "type": "TFNG", "text": "Wolves disappeared ... disease." }
      ]
    },
    {
      "type": "FILL",
      "heading": "Questions 4–6",
      "instruction": "Complete the sentences. Write ONE WORD ONLY from the text.",
      "questions": [
        { "n": 4, "type": "FILL", "pre": "Without wolves, ... near", "post": "." }
      ]
    }
  ]
}
```

Notes for the UI:
- `passageHtml` is a **server-sanitized HTML fragment** authored by an admin.
  The admin encodes reading aids directly in the markup, matching the prototype:
  - glossed words as `<span class="w" data-az="…">word</span>`,
  - evidence spans as `<span class="ev" id="eN">…</span>`.
- The client injects `passageHtml` into the passage container and then
  **discovers** aids from the rendered DOM: it queries `.w` elements to build
  gloss popovers and the word list, and resolves evidence by `getElementById`.
  This is exactly the prototype's approach, so that logic ports unchanged.
- Because this is HTML, the server MUST sanitize it against an allow-list before
  storing/serving (see §9). The client treats it as trusted *after* sanitization.

### 4.2 `POST /api/v1/attempts` — submit and grade

Request:

```json
{
  "passageId": "yellowstone-wolves",
  "elapsedSeconds": 214,
  "answers": [
    { "n": 1, "value": "FALSE" },
    { "n": 4, "value": "riverbanks" }
  ]
}
```

Response:

```json
{
  "score": 5,
  "total": 6,
  "percent": 83,
  "elapsedSeconds": 214,
  "results": [
    {
      "n": 1,
      "correct": true,
      "given": "FALSE",
      "answer": "FALSE",
      "explanation": "Mainly = largely. The text gives the main reason: hunting...",
      "evidenceId": "e1"
    }
  ]
}
```

Notes for the UI:
- The UI renders feedback purely from `results`; it does not know answers before
  this response.
- `explanation` is a **server-sanitized HTML fragment** (same allow-list as
  `passageHtml`, see §9). This keeps admin authoring consistent across passage
  and explanations and preserves the prototype's inline emphasis (`<b>`, `<i>`).
  The client injects it via `innerHTML` only after server sanitization.

---

## 5. Client module structure (`reading.js`)

A single ES module, organised into small units. (If the project later adopts a
build step or framework these map cleanly to components; for now they are
plain functions/objects.)

```
reading.js
├── api            fetchSession(id), submitAttempt(payload)
├── state          current session, answers, timer state, submitting flag
├── render
│     ├── renderPassage(passageHtml)  inject HTML, then index gloss + evidence
│     ├── renderQuestions(groups)     TFNG + FILL controls
│     ├── renderResult(summary)       score box
│     └── renderFeedback(results)     per-question correct/wrong + explanation
├── timer          start(), stop(), reset(), tick()  (counts into overtime)
├── highlight      selection → Highlight button → wrap/unwrap <mark>
├── gloss          tap word → popover; outside tap → dismiss
├── evidence       showInText(evidenceId) → mark span + scroll
└── controller     init(): load → render → wire events; exposes isInProgress()
                   for the app-shell navigation guard (§6.9)
```

> The navbar itself lives in the shared shell (`nav.js` + the layout/fragment,
> §3a); `reading.js` only exposes the in-progress flag the guard reads.

### State shape

```js
state = {
  session: null,        // raw API payload
  submitting: false,
  graded: false,
  timer: { remaining, intervalId, done }
}
```

Answers are read from the DOM at submit time (single source of truth = form
controls), matching the prototype and avoiding duplicate state.

---

## 6. UI behaviour design

### 6.1 Loading / error states (Req 1)

- On `init()`, show a loading indicator in both panels; call `fetchSession`.
- On success → render; then start timer.
- On failure → render an error panel with a Retry button that re-runs `init()`.
  Timer does not start.

### 6.2 Rendering the passage (Req 1, 2)

- Inject `session.passageHtml` into the passage container via `innerHTML`
  (the fragment is server-sanitized; see §9).
- After injection, index the rendered DOM:
  - query `.w` elements → wire gloss popovers and build the word list from each
    element's text and `data-az`;
  - evidence spans (`.ev` with `id`) are resolved later via `getElementById`.
- This mirrors the prototype, which also builds the word list by querying the
  rendered `.w` nodes rather than from separate data.

### 6.3 Questions (Req 5)

- For each group, render heading + instruction.
- TFNG → a `.q` block with a `role="radiogroup"` and three labelled radios
  (TRUE/FALSE/NOT GIVEN).
- FILL → a `.q` block with `pre` text, a `.fill` input, `post` text; input has
  `autocapitalize=off`, `spellcheck=false`, an accessible label.

### 6.4 Timer (Req 4)

- `remaining` initialised from `durationSeconds`.
- `tick()` every 1000 ms: format mm:ss, apply `.low` at/under
  `lowTimeThresholdSeconds`, show `+` prefix once negative.
- `stop()` on submit; `reset()` on restart.

### 6.5 Highlighting (Req 3)

- Port the prototype's selection logic: on `mouseup`/`touchend`, if a non-empty
  selection lies inside the passage, show the floating Highlight button anchored
  to the selection's bounding rect.
- Activating it wraps the range in `<mark>`; clicking an existing `<mark>`
  (with no active selection) unwraps it and re-normalises text nodes.
- Manual `<mark>` styling is distinct from evidence styling (`.ev.on`).

### 6.6 Gloss popover (Req 2)

- Click within passage on a `.w` with no active selection → position the shared
  popover near the word, clamped to viewport width.
- Click elsewhere (outside popover) → hide it.

### 6.7 Submit + feedback (Req 6, 7)

- Submit handler: if `state.submitting || state.graded` → ignore (prevents
  double submit). Set `submitting`, collect answers from DOM, compute elapsed
  from timer, `POST /api/v1/attempts`.
- On success: `graded = true`, stop timer, disable inputs, render result box,
  render per-question feedback (correct/wrong class, given vs correct answer,
  explanation, and a "Show in text" button when `evidenceId` present), scroll
  result into view.
- On failure: clear `submitting`, keep answers, show a retry affordance.
- "Show in text": clear prior `.ev.on`, add it to the mapped evidence element,
  scroll into view.

### 6.8 Restart (Req 8)

- Reset form, hide result + feedback, remove manual `<mark>`s and evidence
  highlights, re-enable inputs, reset+restart timer, scroll to top, clear
  `graded`.

### 6.9 In-progress flag for navigation guard (Req 10.6)

- The controller maintains an "in progress" flag: true once a session is loaded
  and the timer is running, false once the session is submitted/graded or reset
  to an unstarted state.
- The app shell's navbar consults this flag (and a `beforeunload` handler covers
  hard navigations/refresh) to warn before discarding an unsubmitted session.
- Submitting (Req 6) and completing navigation clear the warning path.

---

## 7. Responsive, theming, accessibility (Req 9)

- Layout, dark-mode variables, focus-visible outlines, reduced-motion handling,
  and ARIA roles are carried over from the prototype CSS/markup with no
  behavioural change.
- The two-column `main` collapses to one column under the existing breakpoint
  (~820px).
- Theme follows `prefers-color-scheme`, overridable via `data-theme`.

---

## 8. File layout (UI side)

```
src/main/resources/
├── templates/
│   ├── layout/
│   │   └── main.html             shared app shell (html/head + navbar + slot)
│   ├── fragments/
│   │   └── navbar.html           data-driven navbar fragment
│   └── reading.html              reading content fragment (uses the layout)
└── static/
    ├── css/
    │   ├── app.css               shell + navbar styles, shared theme vars
    │   └── reading.css           ported from prototype <style>
    └── js/
        ├── nav.js                navbar toggle + navigation guard
        └── reading.js            API-driven UI controller (ES module)
```

Shared theme variables (the `:root` custom properties from the prototype) move
to `app.css` so both the shell and feature pages use one source of truth;
`reading.css` keeps only reading-specific rules.

The Thymeleaf controllers and the REST controllers/services are defined in the
backend spec; this UI depends only on the §4 contract and the §3a nav-item model.

---

## 9. Security and integrity

- **Answer key never in the client.** `GET /passages` excludes answers and
  explanations; they appear only in the `POST /attempts` response (Req NFR 1).
- **Admin HTML is sanitized server-side.** `passageHtml` and `explanation` are
  HTML fragments authored by admins. The **server MUST sanitize** them against a
  strict allow-list before storing/serving:
  - allowed tags: `p`, `span`, `mark`, `em`, `strong`, `b`, `i`, `br`;
  - allowed attributes: `class`, `id`, `data-az` (and only on `span`);
    **superseded by the backend** (backend design §8.3): `data-az` became
    `data-g` (meanings come from the API's `glosses` map), `class` is limited
    to `w`/`ev`, `data-g` only on `span.w`, `id` only on `span.ev`; and
    explanations / instructions keep no attributes at all;
  - stripped: `script`, `style`, event handlers (`on*`), `iframe`, inline
    URLs/`javascript:`, and any tag/attribute not on the list.
  - After sanitization the client may safely inject via `innerHTML`.
  - Rationale: this preserves admin authoring flexibility (the reason HTML was
    chosen) while removing the XSS surface that raw `innerHTML` would otherwise
    create (Req NFR 4). Trust boundary is the server sanitizer, not the browser.
- **Double-submit guard** via the `submitting` flag.

---

## 10. Testing strategy (UI side)

- **Rendering tests**: given a mock session payload, the renderer produces the
  expected number of gloss spans, word-list rows, TFNG radio groups, and FILL
  inputs.
- **Interaction tests** (DOM/jsdom or browser test runner):
  - timer formats mm:ss and switches to `+` overtime and `.low` state;
  - highlight wrap/unwrap round-trips and normalises text;
  - gloss popover opens on word tap and closes on outside tap;
  - submit disables inputs, renders score and per-question feedback, and guards
    against double submission;
  - "Show in text" moves the evidence highlight and clears the previous one;
  - restart returns the screen to its initial state.
- **Contract tests**: the client is exercised against a stubbed API matching §4
  so UI work can proceed before the real backend exists.
- **Accessibility checks**: focus-visible on controls, radiogroup semantics,
  labelled inputs.

---

## 11. Open questions

1. ~~Explanation formatting~~ — **Resolved:** explanations are server-sanitized
   HTML fragments, consistent with `passageHtml` (§4.2, §9).
2. ~~Evidence mapping~~ — **Resolved:** evidence is encoded inline in
   `passageHtml` as `<span class="ev" id="eN">`; results reference it by
   `evidenceId`, resolved client-side via `getElementById` (prototype approach).
3. **Session id source** — fixed route param for now; later tied to
   curriculum/stage navigation (future spec).
4. **Sanitizer implementation** — which server-side library/approach enforces
   the §9 allow-list (e.g. a vetted HTML sanitizer). Decided in the backend spec.
