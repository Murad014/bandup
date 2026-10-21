# Requirements — Reading Practice UI

## Overview

The Reading Practice UI is the learner-facing screen where a student completes a
single IELTS-style reading session: read a passage, answer a set of questions
under a timer, submit, and review auto-graded feedback with explanations.

This document specifies the **UI side only**. The UI is delivered as a
Thymeleaf-served page whose dynamic behaviour is driven by a REST API
(`/api/v1/**`). The UI never contains the answer key; it requests the session
from the API and sends the learner's answers to the API for grading.

The behaviour described here is derived from the working prototype at
`ai-docs/prototype/index.html`.

### Scope

- In scope: the single reading-session screen and all of its interactions
  (passage reading aids, question answering, timer, submission, feedback review,
  restart), plus the client-side contract with the REST API. Also in scope: a
  shared **app shell / navbar** that this page plugs into and that future
  features (Grammar, etc.) will reuse.
- Out of scope (future specs): authentication, curriculum/stage navigation
  (within a feature), content authoring, persistence of attempts, and the
  Listening/Writing/Speaking/Grammar feature screens themselves (the navbar only
  links to them).

### Definitions

- **Session**: one passage plus its question set and timer configuration.
- **Question types**: `TFNG` (True / False / Not Given) and `FILL` (gap-fill,
  one word from the text).
- **Evidence**: the sentence/span in the passage that justifies an answer.
- **Vocabulary gloss**: an Azerbaijani translation attached to a marked word.

---

## Requirement 1 — Load and render a reading session

**User story:** As a learner, I want the passage and its questions to load on
screen, so that I can start practising.

#### Acceptance Criteria

1. WHEN the page is opened for a session THEN the UI SHALL request the session
   from `GET /api/v1/passages/{id}` and render it without a full page reload of
   content.
2. WHEN the session data is received THEN the UI SHALL display the passage
   title, the passage text, and the stage/label (e.g. "Stage 1 · Mini passage 4").
3. WHEN the session data is received THEN the UI SHALL render all questions
   grouped by type with their instructions (TFNG group and FILL group), each
   question showing its sequence number.
4. WHILE the session is loading THE UI SHALL show a loading state and SHALL NOT
   show empty question containers.
5. IF the session request fails THEN the UI SHALL show an error state with a
   retry action and SHALL NOT start the timer.
6. WHERE the API response omits answer keys and explanations THE UI SHALL still
   render fully (the UI SHALL NOT depend on answers being present before
   submission).

---

## Requirement 2 — Reading aids (vocabulary gloss and word list)

**User story:** As a learner, I want to see the meaning of difficult words, so
that I can understand the passage in my native language.

#### Acceptance Criteria

1. WHERE a word in the passage has an Azerbaijani gloss THE UI SHALL visually
   mark it as glossed (distinct styling) and expose it as interactive.
2. WHEN a learner taps/clicks a glossed word AND no text is currently selected
   THEN the UI SHALL show a popover containing the word and its Azerbaijani
   meaning, positioned near the word and kept within the viewport.
3. WHEN a learner taps/clicks outside an open popover THEN the UI SHALL dismiss
   the popover.
4. WHEN the session is rendered THEN the UI SHALL build a collapsible word list
   showing every glossed word paired with its Azerbaijani meaning.

---

## Requirement 3 — Passage highlighting

**User story:** As a learner, I want to highlight parts of the passage, so that
I can mark important information while I read.

#### Acceptance Criteria

1. WHEN a learner selects text within the passage THEN the UI SHALL show a
   "Highlight" action anchored to the selection.
2. WHEN the learner activates the Highlight action THEN the UI SHALL wrap the
   selected text in a highlight and clear the selection.
3. WHEN a learner taps/clicks an existing highlight AND no text is currently
   selected THEN the UI SHALL remove that highlight and restore the surrounding
   text flow.
4. IF the selection falls outside the passage region THEN the UI SHALL NOT offer
   the Highlight action.
5. WHERE highlights exist THE UI SHALL keep them visually distinct from
   evidence highlighting used during feedback.

---

## Requirement 4 — Timer

**User story:** As a learner, I want a countdown timer, so that I can practise
under realistic time pressure.

#### Acceptance Criteria

1. WHEN a session starts THEN the UI SHALL display a countdown timer initialised
   to the session's configured duration.
2. WHILE the session is in progress THE UI SHALL update the timer every second
   in mm:ss format.
3. WHEN the remaining time is at or below a low-time threshold THEN the UI SHALL
   apply a visual "low time" emphasis to the timer.
4. WHEN the timer reaches zero THEN the UI SHALL continue counting into overtime,
   displayed with a leading "+" (e.g. "+0:15").
5. WHEN the session is submitted THEN the UI SHALL stop the timer.

---

## Requirement 5 — Answering questions

**User story:** As a learner, I want to record my answers, so that they can be
graded.

#### Acceptance Criteria

1. WHERE a question is of type TFNG THE UI SHALL present exactly the choices
   TRUE, FALSE, NOT GIVEN as a single-select control.
2. WHERE a question is of type FILL THE UI SHALL present a single-line text
   input placed within the sentence, with autocapitalisation and spellcheck
   disabled.
3. WHILE the session is in progress THE UI SHALL allow the learner to change any
   answer freely.
4. WHEN the learner has not answered a question THEN the UI SHALL still allow
   submission (unanswered questions are permitted).

---

## Requirement 6 — Submit and grade

**User story:** As a learner, I want to submit my answers and get a score, so
that I know how I performed.

#### Acceptance Criteria

1. WHEN the learner activates Submit THEN the UI SHALL send all answers and the
   elapsed time to `POST /api/v1/attempts` and SHALL NOT grade answers locally.
2. WHEN grading results are received THEN the UI SHALL display a result summary
   containing score (correct/total), percentage, and time used.
3. WHEN grading results are received THEN the UI SHALL mark each question as
   correct or incorrect with distinct styling.
4. WHEN a question is graded THEN the UI SHALL show the learner's answer, the
   correct answer, and the explanation for that question.
5. WHEN the session is graded THEN the UI SHALL disable all answer inputs to
   prevent further edits.
6. WHEN results are shown THEN the UI SHALL scroll the result summary into view.
7. IF the submission request fails THEN the UI SHALL show an error state, keep
   the learner's answers intact, and allow retrying submission.
8. WHILE a submission is in flight THE UI SHALL prevent duplicate submissions.

---

## Requirement 7 — Evidence review ("Show in text")

**User story:** As a learner, I want to see where the answer comes from in the
passage, so that I can learn how to find it.

#### Acceptance Criteria

1. WHERE a graded question has an evidence reference THE UI SHALL offer a "Show
   in text" action in that question's feedback.
2. WHEN the learner activates "Show in text" THEN the UI SHALL highlight the
   corresponding evidence span in the passage and scroll it into view.
3. WHEN a new evidence span is shown THEN the UI SHALL clear any previously shown
   evidence highlight.

---

## Requirement 8 — Restart

**User story:** As a learner, I want to start the session again, so that I can
retry after reviewing feedback.

#### Acceptance Criteria

1. WHEN the learner activates "Start again" THEN the UI SHALL clear all answers,
   feedback, result summary, evidence highlights, and manual highlights.
2. WHEN the learner activates "Start again" THEN the UI SHALL reset and restart
   the timer from the configured duration.
3. WHEN the session is restarted THEN the UI SHALL re-enable all answer inputs
   and return the view to the top.

---

## Requirement 9 — Responsive, accessible presentation

**User story:** As a learner on any device, I want the screen to be usable and
accessible, so that I can practise on phone or desktop.

#### Acceptance Criteria

1. WHERE the viewport is wide THE UI SHALL present the passage and answer sheet
   side by side; WHERE the viewport is narrow THE UI SHALL stack them in a
   single column.
2. WHERE the device/user prefers dark mode THE UI SHALL render a dark theme, and
   WHERE a theme is explicitly set THE UI SHALL honour the explicit theme.
3. WHEN interactive controls receive keyboard focus THEN the UI SHALL show a
   visible focus indicator.
4. WHERE question groups and controls are rendered THE UI SHALL provide
   appropriate accessible labels and roles (e.g. radio group per TFNG question,
   labelled inputs for FILL).
5. WHERE the user prefers reduced motion THE UI SHALL avoid non-essential
   animated transitions.

---

## Requirement 10 — Global navigation (app shell)

**User story:** As a learner, I want a persistent navigation bar, so that I can
move between features (Reading now; Grammar and other skills later).

#### Acceptance Criteria

1. WHEN any feature page renders THEN the UI SHALL present a persistent top
   navigation bar containing the app brand and the list of features.
2. WHERE a feature is the active page THE navbar SHALL indicate it as the active
   item.
3. WHERE a feature is planned but not yet available (e.g. Grammar) THE navbar
   SHALL show it as a disabled "coming soon" entry rather than a working link.
4. WHERE the navigation list changes (a new feature such as Grammar is added)
   THE navbar SHALL update from a single data-driven source without changes to
   individual feature pages.
5. WHERE the viewport is narrow THE navbar SHALL collapse into a compact
   menu (e.g. a toggle) and remain operable by keyboard.
6. WHEN the learner attempts to navigate away via the navbar WHILE a reading
   session is in progress and not yet submitted THEN the UI SHALL warn that
   progress will be lost and require confirmation before leaving.
7. WHERE the navbar is rendered THE UI SHALL apply the same theming (light/dark)
   and accessibility expectations (focus indicators, accessible labels, current
   -page indication) as the rest of the app.

---

## Non-functional requirements

1. The UI SHALL NOT contain or expose correct answers or explanations prior to
   submission (integrity requirement — answers live server-side only).
2. The UI SHALL remain functional as a single session screen without requiring
   authentication in this phase.
3. The UI SHALL degrade gracefully (visible error state, no silent failures) on
   any API error.
4. The UI SHALL render admin-authored HTML (passage and explanations) that has
   been sanitized server-side against a strict allow-list; the server sanitizer
   is the trust boundary. The UI SHALL NOT render any HTML fragment that has not
   passed server sanitization.
