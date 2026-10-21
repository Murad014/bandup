# Requirements — Admin Panel UI

## Overview

The Admin Panel UI is the privileged, authenticated area where content authors
create and manage **Reading practice sessions**: the passage (authored as an
HTML fragment with inline vocabulary glosses and evidence spans), the question
groups and questions (True/False/Not Given and gap-fill), and — unlike the
learner experience — the **answer key, explanations, and evidence links**.

The content authored here is exactly what the learner-facing Reading UI consumes
(see `../reading-practice-ui/`). This spec defines the **admin UI side**. Like
the reading UI, it is delivered as Thymeleaf-served pages whose behaviour is
driven by a REST API (`/api/v1/admin/**`), built against a stub first.

### Scope

- In scope: the admin area shell/navigation; listing, creating, editing, and
  deleting Reading sessions; authoring passage HTML with gloss/evidence helpers
  and a live preview; authoring questions, answers, explanations and evidence
  links; validation; draft/publish lifecycle; the unsaved-changes guard; and the
  client-side contract with the admin REST API.
- Out of scope (future specs): the authentication mechanism itself (login,
  identity provider, session management), the backend persistence and the
  server-side HTML sanitizer implementation, authoring for non-Reading features
  (Grammar etc.), and media/image upload.

### Relationship to other specs

- Consumes/produces the content model served by the Reading backend. The admin
  **write** endpoints and the learner **read** endpoints share one data store.
- Uses a **dedicated admin layout with a persistent left sidebar** for admin
  navigation. It reuses the shared theme variables and base styles (app.css) but
  does **not** use the public top navbar for navigating within the admin area.
  The public navbar only provides a role-gated **entry point** into the admin
  area.

### Definitions

- **Session**: one Reading passage plus its questions, answer key, and settings.
- **Gloss**: an inline `<span class="w" data-az="…">` marking a word with its
  Azerbaijani meaning.
- **Evidence span**: an inline `<span class="ev" id="eN">` marking the passage
  text that justifies an answer; questions reference it by id.
- **Draft / Published**: lifecycle status controlling learner visibility.

---

## Requirement 1 — Access control (authenticated admin only)

**User story:** As the product owner, I want the admin panel restricted to
authorised authors, so that only trusted users can create or change content.

#### Acceptance Criteria

1. WHEN an unauthenticated user requests any admin page (`/admin/**`) or admin
   API (`/api/v1/admin/**`) THEN the system SHALL deny access (redirect to sign
   in for pages; `401`/`403` for API) and SHALL NOT render or return content.
2. WHEN an authenticated user without the admin role requests an admin page or
   API THEN the system SHALL deny access with a forbidden response.
3. WHERE a user has the admin role THE system SHALL grant access to the admin
   area.
4. WHERE the public top navbar is shown THE Admin entry (the link into the
   admin area) SHALL be visible only to users with the admin role.
5. WHILE the authentication mechanism is not yet implemented THE admin area
   SHALL remain guarded by an explicit access-control layer (not merely hidden
   links), so enabling real auth later does not expose it retroactively.

---

## Requirement 2 — List reading sessions

**User story:** As an author, I want to see all reading sessions, so that I can
find and manage existing content.

#### Acceptance Criteria

1. WHEN the author opens the admin sessions list THEN the UI SHALL request the
   list from `GET /api/v1/admin/passages` and display each session's title,
   stage label, status (draft/published), and question count.
2. WHERE the list is empty THE UI SHALL show an empty state with a call to
   action to create the first session.
3. WHEN the author activates "New session" THEN the UI SHALL navigate to the
   create flow.
4. WHERE a session row is shown THE UI SHALL offer edit, delete, and
   publish/unpublish actions.
5. IF the list request fails THEN the UI SHALL show an error state with retry.

---

## Requirement 3 — Create and edit session metadata

**User story:** As an author, I want to set a session's basic details, so that it
is identified and timed correctly.

#### Acceptance Criteria

1. WHEN creating or editing a session THEN the UI SHALL provide fields for title,
   stage label, duration (seconds or mm:ss), and low-time threshold.
2. WHEN the author saves THEN the UI SHALL send the metadata to the admin API
   (`POST` for create, `PUT` for update) and reflect the saved result.
3. IF a required metadata field is missing or invalid (e.g. non-positive
   duration, threshold greater than duration) THEN the UI SHALL block save and
   show a field-level validation message.
4. WHERE a session is created THE system SHALL assign a stable identifier/slug
   used by the learner URL.

---

## Requirement 4 — Author the passage with reading-aid helpers

**User story:** As an author, I want to write the passage and mark up glosses and
evidence easily, so that I don't have to hand-write HTML.

#### Acceptance Criteria

1. WHEN editing a passage THEN the UI SHALL provide a passage editor whose output
   is the passage HTML fragment consumed by the Reading UI.
2. WHEN the author selects passage text and applies "Add gloss" THEN the UI SHALL
   wrap the selection as a gloss (`<span class="w" data-az="…">`) and prompt for
   the Azerbaijani meaning.
3. WHEN the author selects passage text and applies "Mark evidence" THEN the UI
   SHALL wrap the selection as an evidence span (`<span class="ev" id="eN">`)
   with a unique id.
4. WHEN the author removes a gloss or evidence mark THEN the UI SHALL unwrap it
   and keep the surrounding text intact.
5. WHILE authoring THE UI SHALL show a live preview that renders the passage the
   same way the learner sees it (glossed words and evidence styling).
6. WHERE evidence spans exist THE UI SHALL expose their ids for selection when
   linking questions (Requirement 6).
7. WHERE the author enters passage markup directly THE UI SHALL treat it as HTML
   to be sanitized server-side on save (Requirement 10); the UI SHALL NOT rely on
   client-side sanitization for safety.

---

## Requirement 5 — Manage question groups and questions

**User story:** As an author, I want to build the question set, so that learners
have items to answer.

#### Acceptance Criteria

1. WHEN editing a session THEN the UI SHALL allow adding, reordering, and
   removing question groups, each with a type (TFNG or FILL), a heading, and an
   instruction.
2. WHERE a group is TFNG THE UI SHALL let the author add questions each with a
   statement text.
3. WHERE a group is FILL THE UI SHALL let the author add questions each with a
   sentence split into a leading part and a trailing part (the gap between them).
4. WHEN questions are added or reordered THEN the UI SHALL maintain a unique,
   contiguous question numbering across the session.
5. WHEN the author removes a question THEN the UI SHALL renumber the remaining
   questions and update any dependent references.

---

## Requirement 6 — Author answers, explanations, and evidence links

**User story:** As an author, I want to provide the correct answer, an
explanation, and the supporting evidence for each question, so that grading and
feedback work.

#### Acceptance Criteria

1. WHERE a question is TFNG THE UI SHALL require the author to choose the correct
   answer from TRUE, FALSE, or NOT GIVEN.
2. WHERE a question is FILL THE UI SHALL require at least one accepted answer
   value and MAY allow multiple accepted values.
3. WHEN authoring a question THEN the UI SHALL provide an explanation editor
   whose output is an HTML fragment (sanitized server-side) shown as feedback.
4. WHEN linking evidence THEN the UI SHALL let the author pick from the evidence
   ids present in the passage, OR leave it unset.
5. IF a question references an evidence id that does not exist in the passage
   THEN the UI SHALL flag it as a validation error (Requirement 7).

---

## Requirement 7 — Validation and integrity

**User story:** As an author, I want the panel to catch mistakes, so that I don't
publish broken content.

#### Acceptance Criteria

1. WHEN the author attempts to save or publish THEN the UI SHALL validate that:
   every question has a correct answer; FILL answers are non-empty; TFNG answers
   are one of the allowed values; question numbers are unique and contiguous; and
   every referenced evidence id exists in the passage.
2. IF validation fails THEN the UI SHALL block the action and show messages
   anchored to the offending fields.
3. WHERE the passage defines evidence ids THE UI SHALL warn about evidence spans
   that are not referenced by any question (non-blocking).
4. WHEN the server rejects a save with validation errors THEN the UI SHALL
   display those server-side errors without losing the author's input.

---

## Requirement 8 — Draft and publish lifecycle

**User story:** As an author, I want to control when content goes live, so that
learners only see finished sessions.

#### Acceptance Criteria

1. WHERE a session is a draft THE UI SHALL allow saving freely without passing
   full publish validation.
2. WHEN the author publishes THEN the UI SHALL run full validation
   (Requirement 7) and only publish if it passes.
3. WHEN the author unpublishes a session THEN the UI SHALL set it back to draft
   so learners no longer see it.
4. WHERE a session's status changes THE UI SHALL reflect the new status in the
   list and editor.

---

## Requirement 9 — Delete session

**User story:** As an author, I want to remove a session, so that obsolete
content is cleared.

#### Acceptance Criteria

1. WHEN the author deletes a session THEN the UI SHALL require an explicit
   confirmation naming the session before proceeding.
2. WHEN deletion is confirmed THEN the UI SHALL call the admin API and remove the
   session from the list on success.
3. IF deletion fails THEN the UI SHALL show an error and keep the session listed.

---

## Requirement 10 — Unsaved-changes guard

**User story:** As an author, I want to be warned before losing edits, so that I
don't accidentally discard work.

#### Acceptance Criteria

1. WHILE the editor has unsaved changes THE UI SHALL mark the state as dirty.
2. WHEN the author attempts to navigate away (via the navbar, a link, or
   closing/refreshing the tab) WHILE dirty THEN the UI SHALL warn and require
   confirmation before leaving.
3. WHEN a save completes successfully THEN the UI SHALL clear the dirty state.

---

## Requirement 11 — Admin navigation (sidebar)

**User story:** As an author, I want a persistent sidebar in the admin area, so
that I can move between admin sections quickly.

#### Acceptance Criteria

1. WHERE any admin page is shown THE admin layout SHALL present a persistent
   left sidebar containing the admin brand/area title and the admin navigation
   items (e.g. Dashboard, Sessions; extensible to future content types).
2. WHERE a sidebar item corresponds to the current page THE sidebar SHALL mark
   it as the active item.
3. WHERE the admin navigation set grows THE sidebar SHALL render from a single
   data-driven source without changes to individual admin pages.
4. WHERE the viewport is narrow THE sidebar SHALL collapse into a toggle
   (off-canvas or compact) and remain operable by keyboard.
5. WHERE a sign-out / exit-to-app affordance is needed THE sidebar SHALL provide
   it (exact auth action deferred to the auth spec).
6. WHERE the admin layout is rendered THE UI SHALL apply the shared theming
   (light/dark) and accessibility expectations (focus indicators, accessible
   labels, current-page indication).

---

## Non-functional requirements

1. **Access control is mandatory.** The admin area SHALL be protected by a
   server-side authorization layer; hiding navigation is not sufficient. No admin
   page or admin API SHALL be reachable without the admin role.
2. **Server-side sanitization is the trust boundary.** All author-supplied HTML
   (passage, explanations, instructions) SHALL be sanitized on the server against
   a strict allow-list (per `reading-practice-ui` design §9) before storage. The
   UI SHALL NOT depend on client-side sanitization for safety.
3. **No answer leakage to learners.** Answer keys and explanations authored here
   SHALL only be exposed through admin endpoints, never through the learner
   `GET /api/v1/passages/{id}` response.
4. The admin UI SHALL use a dedicated admin layout with a persistent sidebar
   (Requirement 11), reusing the shared theme variables, responsive behaviour,
   and accessibility expectations (focus indicators, labelled controls, keyboard
   operability) defined for the app. It SHALL NOT use the public top navbar for
   in-admin navigation.
5. The admin UI SHALL degrade gracefully with visible error states on any API
   failure, never failing silently.
