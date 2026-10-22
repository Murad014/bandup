# Requirements — Backend

## Overview

BandUp is one Spring Boot application with **two sides**:

- **Backend (`com.bandup.api`)**: all business logic, organised as bounded
  contexts, exposed only as a REST API under `/api/v1/**`, secured with JWT.
- **Frontend (`com.bandup.ui`)**: Thymeleaf page shells plus static JS/CSS.
  It holds no business logic. The page's JavaScript calls the REST API with
  `Authorization: Bearer <JWT>`.

The two sides talk **only over HTTP**, so the frontend could later be replaced
(e.g. a mobile app) without touching the backend.

The backend is built with **Domain-Driven Design** and a **layered
architecture**: each bounded context has `domain`, `application`,
`infrastructure` and `web` layers. The **domain layer is pure Java** and
depends on no technology.

### Scope

- In scope (phase 1): architecture rules; **Identity** (users, roles,
  email/password login, JWT); **Practice** (learner passage view, submitting
  and grading attempts); **Content** (authoring exercises for the admin
  panel); **Shared kernel** (languages with fallback, errors); HTML
  sanitization.
- Later (phase 2, separate approval): **Catalog** (courses, topics, browse
  and filter), then Google sign-in, email verification and password reset.
- After the first production release: **Media** (images/audio upload and
  serving; needed for diagram questions and Listening).
- Out of scope: frontend changes. Making the UI pages log in and call these
  APIs are separate UI tasks.
- Depends on the database spec: the login tables (`user_credential`,
  `refresh_token`) from migration V3 (`database` task 4, done).

### Definitions

- **Bounded context**: a part of the system with its own model and language
  (Identity, Content, Practice, Catalog, Shared kernel).
- **Aggregate**: objects saved and loaded as one unit, with one root
  (`User`, `Exercise`, `Attempt`). Aggregates reference each other by id.
- **Port / adapter**: an interface owned by the domain or application layer
  (port) and its technology-specific implementation in infrastructure
  (adapter).
- **Access token**: short-lived signed JWT sent as `Authorization: Bearer`.
- **Refresh token**: long-lived random secret in an HttpOnly cookie, used only
  to get a new access token.

---

## Requirement 1 — Architecture

1. THE backend SHALL live in `com.bandup.api.<context>.{domain, application,
   infrastructure, web}`; the frontend SHALL live in `com.bandup.ui` (page
   controllers) and `static/`, `templates/`.
2. `com.bandup.ui` SHALL NOT depend on any class in `com.bandup.api`. The
   frontend reaches the backend only through HTTP calls from the browser.
3. THE domain layer SHALL use only the JDK. It SHALL NOT depend on Spring,
   Jakarta Persistence, Hibernate, Jackson, Lombok or any other library.
4. Dependencies SHALL point inwards: `web → application → domain` and
   `infrastructure → domain`. The domain SHALL NOT depend on any other layer;
   `web` SHALL NOT use `infrastructure` directly.
5. THE application layer MAY use only Spring's `@Service` and
   `@Transactional`; all other technology stays in infrastructure and web.
6. Contexts SHALL reference each other's aggregates by id only, and SHALL read
   another context's data through a port, never through its classes. Only the
   shared kernel may be used by every context.
7. THE rules in 1.2–1.6 SHALL be enforced by automated ArchUnit tests.

## Requirement 2 — Learner passage view

1. WHEN `GET /api/v1/passages/{slug}?lang=` is requested for a PUBLISHED
   reading exercise THEN the API SHALL return the passage, questions and
   options in the shape the reading UI expects (`reading-practice-ui`
   design §4.1, extended per `database` design §5.1).
2. THE response SHALL NOT contain answers, explanations or evidence links.
3. Help text (gloss meanings, translated instructions, stage title) SHALL be
   resolved in the requested language with item-by-item fallback (R6).
4. Question numbers and group headings SHALL be derived from order and
   points; a "Choose N" question SHALL take N numbers ("Questions 7–8").
5. IF the exercise doesn't exist, isn't PUBLISHED or isn't READING THEN the
   API SHALL respond `404`.
6. THE endpoint SHALL be public (no token needed).

## Requirement 3 — Submit and grade an attempt

1. WHEN `POST /api/v1/attempts` is received THEN the server SHALL grade every
   question of the PUBLISHED exercise and SHALL NOT trust any grade from the
   client.
2. Grading SHALL follow `database` design §4.6: case- and space-insensitive
   matching; gap answers over the word limit score 0, with numbers not
   counted when "AND/OR A NUMBER" applies; "Choose N" earns 1 point per
   correct key up to N, and more than N keys score 0.
3. THE server SHALL store the attempt with the exercise revision, the given
   values, a snapshot of the expected answers, and points per question.
4. THE response SHALL contain score, total, percent, elapsed time and, per
   question, the result, the correct answer, the explanation (in the
   learner's language with fallback) and the evidence link.
5. THE endpoint SHALL be public. WHEN a valid access token is sent THEN the
   attempt SHALL be stored for that user; without one it SHALL be anonymous.
6. IF a question number is unknown or repeated, or the elapsed time is
   negative, THEN the server SHALL respond `400`; IF the exercise isn't
   PUBLISHED THEN `404`.

## Requirement 4 — Admin exercise management

1. THE admin API SHALL provide list, get, create, update, delete, publish and
   unpublish under `/api/v1/admin/passages` (`admin-panel-ui` design §4), with
   per-language gloss meanings and explanations (`database` design §5.3).
2. WHEN saving a draft THEN only metadata rules SHALL be enforced; WHEN
   publishing THEN all rules SHALL be enforced: every question has a valid
   answer, evidence links exist, accepted gap answers fit the word limit,
   matching answers aren't reused when options aren't reusable, "Choose N"
   has exactly N keys, a diagram has an image.
3. IF validation fails THEN the API SHALL respond `422` with field-anchored
   errors and SHALL NOT store anything.
4. IF a save carries a stale version THEN the API SHALL respond `409` and SHALL
   NOT overwrite the newer content.
5. WHEN the content of a PUBLISHED exercise is saved THEN its revision SHALL
   increase by one.
6. WHEN an exercise with attempts is deleted THEN it SHALL be archived instead;
   one without attempts SHALL be removed.
7. Every admin endpoint SHALL require an access token with the ADMIN or AUTHOR
   role (R8).

## Requirement 5 — Author HTML

1. THE server SHALL sanitize every author-supplied HTML field (passage,
   instructions, explanations) against the allow-list in `reading-practice-ui`
   design §9, with `data-g` replacing `data-az`, before storing it.
2. WHEN a passage is saved THEN its gloss (`data-g`) and evidence (`ev` id)
   keys SHALL be read from the sanitized HTML, and the stored glosses and
   evidence SHALL be synchronised to exactly those keys.
3. IF a removed evidence key is still used by a question THEN the save SHALL be
   rejected with `422` naming the question.
4. Gloss keys SHALL be allocated by the admin editor. THE server SHALL accept
   them as given, reject a key that isn't `g` + digits or is used twice in one
   passage (`422`), and SHALL NOT renumber them.

## Requirement 6 — Languages

1. THE requested help language SHALL be taken from `?lang=` (or the request
   body), else the logged-in user's support language, else the first
   `Accept-Language` tag, else English.
2. Codes SHALL match case-insensitively; an unknown regional code SHALL use its
   base language (`az-AZ` → `az`); anything else unknown SHALL use English.
3. Each piece of help text SHALL be resolved on its own along the language's
   fallback chain (`language.fallback_code`), which always ends at English.
   Text missing in every chain language SHALL be omitted.

## Requirement 7 — Errors

1. Every error response SHALL use the Problem Details format (RFC 9457).
2. Validation errors (`422`) SHALL add an `errors` list of
   `{ field, message }`, matching `admin-panel-ui` design §4.3.
3. Database constraint violations SHALL be translated to clear `409`/`422`
   messages, never exposed as raw SQL errors.
4. Missing/invalid/expired tokens SHALL get `401`; a valid token without the
   required role SHALL get `403`.

## Requirement 8 — Identity and authentication (JWT)

1. A learner SHALL be able to register with email, password, display name and
   languages (`POST /api/v1/auth/register`). Emails are unique and stored
   lower-case; passwords SHALL be at least 10 characters.
2. WHEN `POST /api/v1/auth/login` succeeds THEN the API SHALL return an access
   token (JWT, valid 15 minutes) in the body and set a refresh token in an
   `HttpOnly`, `Secure`, `SameSite=Strict` cookie limited to
   `/api/v1/auth` (valid 14 days).
3. IF login fails THEN the API SHALL respond `401` with the same message for a
   wrong email and a wrong password.
4. WHEN `POST /api/v1/auth/refresh` is called with a valid refresh cookie THEN
   the API SHALL return a new access token and **rotate** the refresh token
   (the old one stops working). IF an already-used refresh token is presented
   THEN all of that user's refresh tokens SHALL be revoked.
5. WHEN `POST /api/v1/auth/logout` is called THEN the refresh token SHALL be
   revoked and the cookie cleared.
6. `GET /api/v1/auth/me` SHALL return the current user's public id, display
   name, roles and languages.
7. THE access token SHALL carry the user's public id (never the internal id),
   roles and support language, and SHALL be signed with a server secret from
   configuration (never committed).
8. Passwords SHALL be stored only as BCrypt hashes; refresh tokens only as
   SHA-256 hashes.
9. THE first admin SHALL be created at startup from configuration
   (`BANDUP_ADMIN_EMAIL`, `BANDUP_ADMIN_PASSWORD`) when no admin exists.
10. Disabled or deleted users SHALL NOT be able to log in or refresh.

---

## Non-functional requirements

1. **No answer leakage.** Answers and explanations SHALL only leave the server
   in the graded-attempt response and through admin endpoints.
2. **Security by endpoint, not by page.** Page shells under `com.bandup.ui` are
   public HTML without data. Data access is decided only by the API's JWT
   rules. The dev-only `AdminAccessFilter` / `app.admin.dev-allow` SHALL be
   removed when Identity is in place.
3. **Stateless API.** The API SHALL NOT use server sessions; every request is
   authorised by its access token (the refresh endpoint by its cookie).
4. **Testing.** Domain logic SHALL be unit-tested without Spring. Adapters and
   APIs SHALL be tested against PostgreSQL via Testcontainers. Grading rules
   SHALL be covered by a shared case file the browser stub's tests can reuse.
5. **Schema ownership.** The backend SHALL NOT change the schema; Hibernate
   runs with `ddl-auto=validate`. Schema changes are Flyway migrations in the
   database spec.
