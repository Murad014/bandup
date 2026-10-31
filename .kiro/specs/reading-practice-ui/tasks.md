# Tasks — Reading Practice UI

Incremental, testable implementation plan for the **UI side** of the Reading
Practice feature. Tasks are ordered so the UI can be built and verified against a
**stubbed API** before the real backend exists. Each task lists the requirements
it satisfies.

Conventions:
- The UI consumes the §4 API contract from `design.md`.
- All API text is rendered via `textContent` / text nodes (no `innerHTML`).
- "Verify" means an automated test and/or a manual check in the browser.

---

- [x] 1. Build the shared app shell + navbar layout
  - Create `templates/layout/main.html` (html/head + navbar + content slot) and
    `templates/fragments/navbar.html` rendered from a data-driven nav-item list.
  - Move shared theme variables into `static/css/app.css`; style the navbar
    (brand, items, active state, disabled "coming soon" badge, responsive toggle).
  - Add `static/js/nav.js` for the responsive toggle and the navigation guard
    hook (reads an "in progress" flag exposed by the active feature).
  - Seed the nav list with Reading (`enabled`) and Grammar (`comingSoon`).
  - _Verify_: shell renders with navbar; active item marked `aria-current`;
    Grammar shows as disabled "soon"; toggle works by keyboard at narrow widths.
  - _Requirements: 10.1–10.5, 10.7, 9 (theme shell)_

- [x] 2. Scaffold the reading page into the shell + static assets
  - Create `templates/reading.html` as a content fragment that decorates
    `layout/main.html`: header bar (title slot, timer slot, Submit), two-panel
    `main`, floating Highlight button and popover. Embed only `data-session-id`
    and the API base path on a root element.
  - Move the prototype's `<style>` into `static/css/reading.css` (minus the
    theme vars now in `app.css`).
  - Create an empty `static/js/reading.js` loaded as a module.
  - Add `ReadingPageController` with `GET /reading/{id}` returning the `reading`
    view with the nav-item list + `activeKey="reading"` in the model.
  - _Verify_: visiting `/reading/{id}` serves the styled empty shell with navbar;
    the JS module loads.
  - _Requirements: 1 (delivery), 9 (layout/theme shell), 10 (reuse of shell)_

- [x] 3. Add a stub API client and session fixture
  - Implement `api.fetchSession(id)` and `api.submitAttempt(payload)` against the
    §4 contract.
  - Add a local JSON fixture for the Yellowstone session (`passageHtml` with
    inline `.w`/`.ev` markup, question groups, questions with NO answers) to
    develop/test the UI independently of the backend.
  - Provide a toggle to point the client at the fixture vs. the real endpoints.
  - _Verify_: unit test that `fetchSession` resolves the fixture into the
    expected shape; `submitAttempt` posts the expected body.
  - _Requirements: 1, 6 (client contract)_

- [x] 4. Render passage from HTML (+ loading/error states)
  - Implement `render.renderPassage`: inject `session.passageHtml` into the
    passage container via `innerHTML` (fragment is server-sanitized), then index
    the DOM — collect `.w` elements for gloss/word-list and leave `.ev` spans for
    evidence lookup.
  - Implement loading state (both panels) and error state with a Retry action
    that re-runs `init()`.
  - Wire `controller.init()`: show loading → fetch → render → (timer started in
    a later task).
  - _Verify_: given the fixture, the passage renders with the correct count of
    `.w` spans and preserves `.ev` ids; failure path shows the error + Retry.
  - _Requirements: 1.1–1.6_

- [x] 5. Build the vocabulary word list and gloss popover
  - Populate the collapsible word list by querying the rendered `.w` elements
    (word ↔ `data-az`).
  - Implement `gloss`: tap a `.w` (no active selection) → position shared popover
    near the word, clamped to viewport; tap outside → dismiss.
  - _Verify_: word-list row count equals gloss count; popover opens on word tap,
    shows word + AZ, and closes on outside tap.
  - _Requirements: 2.1–2.4_

- [x] 6. Render questions (TFNG + FILL)
  - Implement `render.renderQuestions`: group heading + instruction; TFNG as a
    `role="radiogroup"` with TRUE/FALSE/NOT GIVEN labelled radios; FILL as
    `pre` + `.fill` input (`autocapitalize=off`, `spellcheck=false`, labelled)
    + `post`.
  - Allow free answer changes; allow unanswered questions.
  - _Verify_: fixture produces the expected number of radio groups and fill
    inputs with correct labels/attributes.
  - _Requirements: 5.1–5.4_

- [x] 7. Implement the timer
  - Implement `timer.start/stop/reset/tick`: initialise from `durationSeconds`,
    update mm:ss each second, apply `.low` at/under `lowTimeThresholdSeconds`,
    count into overtime with `+` prefix.
  - Start the timer only after a successful session load (Task 4); not on error.
  - Set the controller "in progress" flag true while running (for the nav guard).
  - _Verify_: tests for mm:ss formatting, `.low` threshold toggle, and overtime
    `+` display (use fake timers).
  - _Requirements: 4.1–4.5_

- [x] 8. Implement passage highlighting
  - Port selection handling: show floating Highlight button for a non-empty
    selection inside the passage; activating wraps the range in `<mark>`.
  - Tapping an existing `<mark>` (no active selection) unwraps it and
    normalises text nodes.
  - Keep manual highlight styling distinct from evidence styling.
  - _Verify_: wrap→unwrap round-trip restores original text; selection outside
    the passage offers no Highlight button.
  - _Requirements: 3.1–3.5_

- [x] 9. Implement submit + grading feedback
  - Submit handler (form submit + header Submit button): guard against double
    submit via `submitting`/`graded`; collect answers from the DOM; compute
    elapsed from the timer; call `submitAttempt`.
  - On success: stop timer, clear "in progress" flag, disable inputs, render
    result box (score/total, percent, time), render per-question feedback
    (correct/wrong class, given vs correct answer, sanitized `explanation` HTML
    via `innerHTML`), scroll result into view.
  - On failure: clear `submitting`, keep answers, show retry affordance.
  - _Verify_: with a stubbed grade response, inputs disable, result + per-question
    feedback render correctly, double-submit is prevented, and the error path
    preserves answers.
  - _Requirements: 6.1–6.8_

- [x] 10. Implement "Show in text" evidence review
  - Resolve a result's `evidenceId` to the passage DOM via `getElementById`
    (the `.ev` span injected with the passage HTML).
  - On "Show in text": clear prior `.ev.on`, mark the evidence span, scroll it
    into view.
  - _Verify_: activating evidence on one question highlights its span and clears
    any previously shown evidence.
  - _Requirements: 7.1–7.3_

- [x] 11. Implement restart
  - "Start again": reset form, hide result + feedback, remove manual `<mark>`s
    and evidence highlights, re-enable inputs, reset+restart timer, scroll to
    top, clear `graded`.
  - _Verify_: after grading then restart, the screen matches initial state
    (inputs enabled, no feedback, fresh timer).
  - _Requirements: 8.1–8.3_

- [x] 12. Navigation guard for in-progress sessions
  - Wire `nav.js` to the controller's `isInProgress()` plus a `beforeunload`
    handler: warn and require confirmation before leaving an unsubmitted session;
    allow leaving once submitted or reset.
  - _Verify_: navbar link / refresh during a running session prompts; after
    submit or restart it does not.
  - _Requirements: 10.6_

- [x] 13. Accessibility and responsive pass
  - Confirm focus-visible indicators, radiogroup semantics per TFNG question,
    labelled FILL inputs, reduced-motion handling, and **navbar** semantics
    (current-page indication, keyboard-operable toggle).
  - Confirm two-column ↔ single-column behaviour and navbar collapse at the
    breakpoint, and dark/light theming (`prefers-color-scheme` + `data-theme`)
    across shell and page.
  - _Verify_: keyboard-only walkthrough of a full session including navbar;
    layout check at narrow and wide widths; theme check in both modes.
  - _Requirements: 9.1–9.5, 10.5, 10.7_

- [x] 14. End-to-end flow against the stub
  - Wire all modules in `controller.init()` and run a full session against the
    stub: load → read (gloss/highlight) → answer → submit → review (feedback +
    evidence) → restart, within the app shell/navbar.
  - Remove the fixture toggle's default-to-stub once the real API is available
    (handoff point to the backend spec).
  - _Verify_: automated happy-path test covering the full loop; manual
    click-through confirms the idea end to end.
  - _Requirements: 1–10 (integration)_

- [x] 15. Personal reading settings (asked by the user)
  - "Aa" panel: passage/question text size, layout (left/right/stacked), page
    width, line spacing, font, colours (site/sepia/dark), cover, reset; the
    draggable divider; a one-time tip announcing the feature. Saved in
    localStorage and applied before the page is drawn (design §7.1).
  - _Verify_: `ReadingBrowserTest.readingSettingsApplyAtOnceAndAreKeptAfterARefresh`
    (change all settings, check the page, refresh, check again, reset); a
    mutation (saving disabled) makes it fail. Screenshots checked in every mode.
  - _Requirements: 11.1–11.7_

- [x] 16. Account button in the navbar (asked by the user)
  - Avatar + name chip with a menu (name, role, My progress, Sign out),
    replacing the plain name and "Sign out" link (design §7.0).
  - _Verify_: `AuthBrowserTest.theAccountButtonShowsTheLearnerAndItsMenuSignsOut`;
    the Selenium page object signs out through the menu; screenshots on
    desktop and phone.
  - _Requirements: 10.8_

- [x] 17. Highlight question text like the passage (asked by the user)
  - The Highlight button also works on the questions panel: stems,
    instructions, option labels; never across an answer box, select or
    button. Tapping a highlight on an option's text removes it without
    ticking the option. "Start again" clears both areas (design §6.5).
  - _Verify_: `ReadingBrowserTest.questionTextCanBeHighlightedLikeThePassageButNeverAcrossAnAnswerControl`
    (passage, question stem, a selection over an answer box is refused, an
    option label); a mutation (passage-only rule) makes it fail.
  - _Requirements: 3.1, 3.4, 3.6, 3.7_

---

## Resolve before/while implementing (from design §11)

- ~~Explanation formatting~~ — Resolved: sanitized admin HTML (Task 8).
- ~~Evidence mapping~~ — Resolved: inline `.ev` spans in `passageHtml`,
  resolved by `evidenceId` (Tasks 3 & 9).
- Session id source: fixed route param now; curriculum navigation is a future
  spec. Affects Task 1.
- Server-side HTML sanitizer: the allow-list enforcement for `passageHtml` and
  `explanation` is a backend-spec concern, but the UI depends on it being in
  place before trusting any fragment (Tasks 3 & 8).
