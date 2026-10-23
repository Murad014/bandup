# Tasks — Frontend

Connects the existing pages (templates, JS, CSS from `reading-practice-ui`
and `admin-panel-ui`) to the finished backend. All tasks are **[Frontend]**
only. Each task is done only when requested, one at a
time, and ends green.

This file holds the **foundation** (page shells, sign-in) and the
**build order**. Each area has its own task list, numbered from 1.

- **Contracts:** learner API = backend design §7.4; admin API = backend
  design §8.4 (supersedes `admin-panel-ui` design §4); auth = backend design
  §6.2–6.4; frontend needs = backend design §10.
- **Rules:** `com.bandup.ui` never imports `com.bandup.api` (ArchUnit rule 1).
  Pages are public shells without data; the API decides access. Text from the
  API goes in with `textContent`; only server-sanitized HTML fields
  (`passageHtml`, `instruction`, `explanation`) use `innerHTML`.
- **Verify** = MockMvc tests (Gradle, like the backend) + headless Chrome
  runs against the real app. No Node, no npm, no build step: the browser runs
  the plain JS files as they are.

## Build order

| Step | Tasks | File |
|------|-------|------|
| 1 | Foundation 1–4: page shells, `auth.js`, sign-in pages, admin guard | this file |
| 2 | Reading 1–5: learner API, all question types, results, help language, remove fixture | [`reading/tasks.md`](reading/tasks.md) |
| 3 | Admin 1–7: admin API, new editor model, glosses, all types, save/publish flow, remove fixture | [`admin/tasks.md`](admin/tasks.md) |
| 4 | Final 1: end-to-end pass | this file |

Reading and Admin both need foundation 1–2; Admin also needs 3–4.

---

## Foundation

- [x] 1. Page shells in `com.bandup.ui`
  - Controllers for the routes the templates already use: `/` (home),
    `/reading/{slug}`, `/admin`, `/admin/sessions`, `/admin/sessions/new`,
    `/admin/sessions/{slug}/edit` (the link `sessions.js` builds); `/login`
    and `/register` come with task 3.
  - `NavigationAdvice` supplies `navItems` and `adminNav` (data only, no
    user state). Remove `isAdmin` from `navbar.html`: the Admin link is
    shown by JS from the token's roles (task 2).
  - Pages embed only what JS needs (`data-session-id`, `data-api-base`);
    every page is public — admin data is protected by the API.
  - _Verify_: MockMvc: each route 200 with its template; no answers or
    exercise data in any page HTML; unknown slug still gets the shell (the
    API's 404 is shown by JS); ArchUnit rule 1 still green.
  - _Requirements: backend NFR 2_
  - _Done_:
    - `com.bandup.ui`: `HomeController`, `ReadingPageController`, `NavItem`,
      `NavigationAdvice`; `com.bandup.ui.admin`: `AdminPageController`,
      `AdminNavItem`, `AdminNavigationAdvice` (same items and labels as the
      removed `com.bandup.web` classes).
    - `navbar.html`: the server-side `isAdmin` check became
      `<li data-nav-admin hidden>`, shown by `auth.js` in task 2.
    - `app.css`: `[hidden] { display: none !important; }` — Chrome showed the
      hidden Admin link because `.app-nav__item` sets `display: flex`.
    - The new-session page has no `data-session-id` (Thymeleaf drops the
      empty attribute); `editor.js` already reads that as "new".
  - _Verified_: `UiPagesTest` (6): all six routes 200 with their templates
    for anonymous visitors; navbar items, active item, hidden Admin link and
    the CSS rule; reading page carries only slug + API base, no passage
    text, glosses or answers; unknown slug gets the shell; a hostile slug is
    escaped; admin pages have the sidebar and no data.
    `SecurityRulesTest` updated: pages (admin ones too) are public now.
    Real app on the dev database (now at V5): every page 200, learner API
    200, admin API 401 without a token; Chrome screenshots of the reading
    page and the editor render (still on their stubs until reading /
    admin task 1). 360 tests green.

- [x] 2. `auth.js` — tokens in the browser
  - Access token kept **in memory only** (never `localStorage`); on page load
    one silent `POST /api/v1/auth/refresh` (cookie) restores the session.
  - `apiFetch(url, options)`: adds `Authorization: Bearer …`, on `401`
    refreshes once and retries once, then reports "signed out".
  - **Single-flight refresh across tabs** (backend open decision 4): only one
    tab refreshes at a time (Web Locks API `navigator.locks`, fallback
    `BroadcastChannel`); the others wait and reuse its new token, so two tabs
    never present the same refresh token.
  - `currentUser()` from `GET /api/v1/auth/me` (id, displayName, roles,
    languages); `onChange(listener)`; `logout()` (`POST /auth/logout`, forget
    token, notify tabs).
  - Navbar: shows the user's name + Sign out when signed in, Sign in
    otherwise; the Admin link when roles include ADMIN or AUTHOR.
  - _Verify_: headless Chrome against the real app: token never written to storage;
    401 → one refresh + one retry; refresh failure → signed out; two
    simultaneous callers → one refresh request; logout clears and notifies.
    Manual: reload keeps you signed in; two tabs reloading together stay
    signed in.
  - _Requirements: backend 8.2, 8.4, 8.5, 8.6_
  - _Done_: `static/js/auth.js` (memory-only token, silent refresh on load,
    refresh a minute before expiry, single-flight refresh: Web Locks across
    tabs + BroadcastChannel to share the new token / sign-out, `apiFetch` with
    one retry, `login`, `register`, `logout`, `safeNext`, `ApiError` with
    `errors[]`); `account-nav.js` + navbar markup (Sign in with `?next=`,
    name + Sign out, Admin link for ADMIN/AUTHOR).
  - _Verified_: `AuthBrowserTest` (6, headless Chrome over the DevTools
    protocol with the JDK's WebSocket client — `HeadlessChrome` test helper,
    no new dependency; skipped without Chrome): anonymous navbar + `next`
    link; learner signs in, stays signed in after reload, `apiFetch` works,
    nothing in storage, refresh cookie invisible to scripts; admin sees the
    Admin link; two tabs reloading at once (3 rounds) stay signed in —
    mutation check: without the Web Lock this test fails (the server sees a
    reused refresh token); sign-out ends the session in both tabs; wrong
    password → one 401 message; `safeNext` rejects `//evil.com`,
    `https://…`, `/\evil`, relative paths. Stable over 3 runs.

- [x] 3. Login and register pages
  - `templates/login.html`, `templates/register.html` in the main layout;
    `login.js`, `register.js` using `auth.js`.
  - Login: email + password; `401` → "Wrong email or password." (one
    message for every failure, as the API). After login go to `?next=`
    (same-site paths only) or home.
  - Register: email, password (≥ 10 characters), display name, help language
    (from enabled support languages); `422` → each field's message under its
    field, input kept; `409` → "This email already has an account"; on
    success log in with the same credentials, then continue as login.
  - _Verify_: headless Chrome for form → request mapping, error display, `next`
    open-redirect guard (`//evil.com`, `https://…` rejected). Manual: sign
    up, sign out, sign in, wrong password.
  - _Requirements: backend 8.1–8.3_
  - _Done_: `AuthPageController` (`/login`, `/register`), `login.html`,
    `register.html`, `auth.css`, `login.js`, `register.js`, `auth-forms.js`.
    Help-language choices are the enabled support languages (az, en) written
    in the template; the browser's language is preselected. Page scripts sit
    inside `<main>` (the layout keeps only that element — a first version
    put them after it and they never loaded).
  - **Backend bug found and fixed:** a too-long display name was checked only
    after email/password/languages (and after BCrypt), so it wasn't reported
    with the other fields (requirement 8.1). Failing test first
    (`AuthApiTest.aTooLongDisplayNameIsReportedTogetherWithTheOtherFields`),
    then `User.displayName(raw)` is collected with the other checks.
  - _Verified_: `SignInPagesBrowserTest` (5, Chrome): sign-up signs in and
    returns to `next`, support language saved; every field error under its
    field, input kept; taken email at the email field; wrong password → one
    message, password cleared, then sign-in → `next`; foreign `next`
    (`https://…`, `//…`) → home, links keep `next`.

- [x] 4. Admin guard in the browser
  - Admin pages start with `currentUser()`: not signed in → redirect to
    `/login?next=<this page>`; signed in without ADMIN/AUTHOR → "You don't
    have access" message (no admin UI rendered).
  - Any admin API `401` after a failed refresh → same redirect (keeping
    unsaved editor content: admin task 5).
  - _Verify_: headless Chrome for the decision logic; manual with LEARNER and ADMIN
    accounts (the first admin comes from `BANDUP_ADMIN_EMAIL` /
    `BANDUP_ADMIN_PASSWORD`).
  - _Requirements: backend 4.7_
  - _Done_: `static/js/admin/guard.js` loaded by `admin/layout.html` (every
    admin page, dashboard included); `adminReady` awaited by the page
    scripts; admin content hidden until checked; account name + Sign out in
    the sidebar; signing out in any tab leaves the admin area. Keeping
    unsaved editor content across a forced sign-in moved to admin task 5
    (the editor is rewritten there).
  - _Verified_: `AdminGuardBrowserTest` (4, Chrome): signed out → login with
    `next` → back to `/admin/sessions` as admin; learner → "You don't have
    access", no admin UI; content hidden until the check passes; sidebar
    sign-out → home, signed out. 376 tests green.

---

## Final

- [x] 1. End-to-end pass
  - Real app + dev database: admin signs in → creates an exercise with every
    supported question type → publish blocked with errors → fixes → publishes
    → learner (anonymous and signed in) opens it in az and en → submits →
    sees results with explanations and evidence → admin edits it (revision 2)
    → deletes it (archived, attempts kept).
  - Accessibility and responsive re-check of the new pages and controls
    (keyboard, focus, labels, narrow widths).
  - _Verify_: written checklist with results in this task; all suites green.
  - _Done_ — checklist, each step run in headless Chrome against the real
    app and database (browser tests in `src/test/java/com/bandup/browser/`):

    | Step | Test | Result |
    |------|------|--------|
    | Admin signs in, sees the Admin link | `AuthBrowserTest.anAdminSeesTheAdminLink` | ✓ |
    | Admin writes an exercise in the editor (passage, gloss + az meaning, TFNG, evidence) and saves it | `AdminBrowserTest.anAuthorWritesAndPublishes…` | ✓ |
    | Publish blocked → errors at their fields, still draft | `AdminBrowserTest.publishIsBlockedWithErrorsAtTheirFields` | ✓ |
    | Fix and publish → a learner opens it with the meaning | `AdminBrowserTest.anAuthorWritesAndPublishes…` | ✓ |
    | Every supported question type (made through the admin API) renders and is graded | `ReadingBrowserTest.everyQuestionFamilyRendersAndIsGradedByTheServer` | ✓ 6/8 with the partial |
    | Learner anonymous and signed in, az and en help | `ReadingBrowserTest` (Wolves az, switch to en, signed-in attempt stored) | ✓ |
    | Results with explanations and evidence, restart | `ReadingBrowserTest.aPerfectWolvesAttempt…` | ✓ |
    | Admin edits a published exercise → revision 2 | `AdminBrowserTest.savingAPublishedExercise…` | ✓ |
    | Delete with attempts → archived, attempts kept | `AdminBrowserTest.deletingAnExerciseWithAttemptsArchivesIt` | ✓ |
    | Two authors at once → the second gets "reload" | `AdminBrowserTest.aStaleSaveOffersAReload` | ✓ |
    | Signed out mid-edit → work restored after sign-in | `AdminBrowserTest.unsavedWorkSurvivesBeingSignedOut` | ✓ |
    | Accessibility: every visible control on every page has a name, images have alt text; the reading page fits a 375 px phone without sideways scrolling | `AccessibilityBrowserTest` | ✓ after a fix |

  - Fixed on the way: the editor's generated inputs weren't linked to their
    labels (screen readers announced nameless inputs) — `field()` now sets
    `label.htmlFor`, option inputs got `aria-label`s. The word-limit hint is
    hidden when the instruction already says it.
  - Browser tests block Google Fonts (tests must not depend on the
    internet: a slow font host made one run hang) and give Chrome 60 s to
    start. `SeedDataTest` now requires the editor's type catalog to equal
    the database's (all 13 types).
  - **393 tests green** (33 of them in Chrome), no Node, no new dependency.

---

## UI scenarios you can watch (Selenium)

- `sh gradlew uiTest` opens a **visible Chrome** and runs 12 user scenarios,
  slowed down (`-PslowMs=700` by default), outlining every element before it
  is used. `sh gradlew uiTest -Pheadless` runs them without a window.
  `sh gradlew test` doesn't run them (tag `ui`).
- Every step saves a screenshot: `build/ui-screenshots/<Class>/<scenario>/`;
  report: `build/reports/tests/uiTest/index.html`.
- Code: `src/test/java/com/bandup/uitest/` — `Ui` (highlight, pause,
  screenshot), `UiTestBase` (real app + Chrome per scenario), page objects
  `NavBar`, `LoginPage`, `RegisterPage`, `ReadingPage`, `AdminListPage`,
  `EditorPage`, scenarios `LearnerScenariosTest` (6) and
  `AuthorScenariosTest` (6).
- Dependency: `selenium-java` (test only, version from Spring Boot, chosen by
  the user); Selenium Manager fetches the matching ChromeDriver itself.
- _Verified_: 12/12 pass headless; visible run checked.

---

## Catalog page (phase 2)

- [x] `/reading` is the reading catalog — see `../backend/catalog/tasks.md`
  (built together with its API). Navbar "Reading" and home link to it; the
  passage page stays at `/reading/{slug}`.

## Redesign: bright & friendly (user choice)

- [x] Design system in `app.css`: cream background, coral primary, teal /
  sun / sky / grape accents, Nunito for the interface (Literata stays for
  passages), rounded pill buttons, soft shadows. Light by default; dark only
  when picked with the toggle (`theme.js` no longer follows the OS).
- [x] Navbar logo mark, "Sign in" as the main button, footer on every page
  (hidden on the reading page).
- [x] Home: hero with illustration, then "What do you want to practise?" —
  the learner picks a skill first (Reading → `/reading`; Grammar "soon", not
  a link), then "how it works" steps. No passages on the home page (user
  request; "Fresh passages" and `home.js` removed).
- [x] Catalog: header banner, chip filters, cards with a cover image, a
  "✓ Done" badge and "Best score n/m".
- [x] Passage cards three per row on desktop (two under 960 px, one under
  600 px), user request.
- [x] Reading: cover banner above the passage; restyled bar, timer,
  questions and result.
- [x] Sign in / sign up cards; admin sidebar, cards and fields restyled.
- [x] Covers (backend requirement 10): `covers.js` picks the uploaded cover
  or the topic illustration (`static/img/covers/*.svg`, five topics +
  default). Admin editor: "Upload image" with preview, "Remove", errors from
  the server; `apiFetch` accepts a `form` (multipart) body.
- _Verified_: `CatalogBrowserTest` (cover src, home skill choice with no
  passages, three columns, passage cover), full suite 424 green, 13 Selenium scenarios green;
  screenshots of home, catalog, reading and sign-in checked by eye.
