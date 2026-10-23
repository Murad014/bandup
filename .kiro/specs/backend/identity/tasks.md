# Tasks — Backend · Identity context

Users, roles, email/password login, JWT access tokens, rotating refresh tokens.
**[Backend]** only. **Nothing is started.** Each task is done only when
requested, one at a time, and ends green (`sh gradlew test`).

- **Depends on:** foundation tasks 1–3 in `../tasks.md` (architecture, shared
  kernel); `database` task 4 (V3 login tables, done).
- **References:** requirements and design sections refer to
  `../requirements.md` and `../design.md`.

---

- [x] 1. Identity — domain
  - In `com.bandup.api.identity.domain`, JDK + shared kernel only: `User`
    (aggregate: register learner, create admin, restore, `canAuthenticate`,
    `canAuthor`, disable/delete, change password), `UserId`, `Email`,
    `NewPassword`, `PasswordHash`, `Role`, `UserStatus`, `RefreshToken`
    (aggregate), `TokenHash`, `FamilyId`, `RefreshTokenRotation` (domain
    service), ports `UserRepository`, `RefreshTokenRepository`.
  - Refinements of design §6.1 (behaviour unchanged):
    - `UserId` is the public UUID (JWT `sub`); the database's numeric id stays
      inside the persistence adapter. A `RefreshToken` is identified by its hash.
    - Rotation, reuse detection and family revoke are decided in one pure
      domain service, `RefreshTokenRotation`; the use case only loads and saves.
    - `NewPassword` also caps passwords at 72 bytes, because BCrypt ignores
      anything longer.
  - _Verified_: 39 unit tests, no Spring: email trimmed/lower-cased and
    validated; password ≥ 10 characters (counted as characters, not bytes) and
    ≤ 72 bytes, never printed; `canAuthenticate` only when ACTIVE; roles;
    display name rules; token states (usable/expired at the exact expiry/
    revoked/replaced, replaced beats expired); rotate → old unusable, new one
    in the same family with a fresh 14 days; reuse → whole family revoked,
    including the newer token; expired/revoked → rejected with nothing
    changed; logout reports only changed tokens. ArchUnit passes. 155 tests
    green.
  - _Requirements: 8_

- [x] 2. Identity — adapters + security configuration
  - Uses the login tables from `database` task 4 (V3, done).
  - Added `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`
    (+ `spring-boot-starter-security-test` for tests).
  - identity.application ports: `PasswordHasher`, `AccessTokenIssuer`,
    `TokenGenerator`. identity.infrastructure: `BCryptPasswordHasher` (cost 12),
    `SecureRandomTokenGenerator` (32 random bytes, base64url; SHA-256 hex hash),
    `JwtAccessTokenIssuer` (claims of design §6.3), JPA adapters
    `JpaUserRepository` (`user_credential` as a secondary table) and
    `JpaRefreshTokenRepository` (saves new tokens first, then links
    `replaced_by`). Numeric ids never leave the adapters.
  - shared: `CurrentUser` port + `JwtCurrentUser` adapter (design §5);
    `JwtConfig` (HS256 key from `BANDUP_JWT_SECRET`, ≥ 32 bytes, else a random
    dev key with a warning; decoder checks signature, expiry, issuer);
    `SecurityConfig` (stateless, no CSRF, rules of design §6.4; filter
    401/403 go through `ApiExceptionHandler`, so they're Problem Details with
    `WWW-Authenticate: Bearer`). JWT settings documented in `application.yaml`.
  - Behaviour to know: an invalid/expired token is rejected (401) even on
    public endpoints (the browser should refresh, not silently become
    anonymous); unknown `/api/**` paths are denied (401/403) per §6.4.
  - _Verified_: `IdentityPersistenceTest` (5, Testcontainers + `validate`:
    user round-trip incl. hash in `user_credential`, email/role lookups,
    updates, rotation stored and linked, reuse revokes the stored family),
    `CryptoAdaptersTest` (4: BCrypt, SHA-256 test vector, url-safe unique
    tokens), `JwtAccessTokenIssuerTest` (claims, 15-min expiry, no private
    data), `SecurityRulesTest` (12: every endpoint group with no/learner/
    author/admin tokens; expired, tampered, other-key, other-issuer and
    garbage tokens → 401; pages/static not behind API rules; no session
    cookie). `ApiExceptionHandlerTest` now runs without security filters.
    Live app: starts without the secret (warning), no default password,
    admin API → 401 Problem Details. 177 tests green.
  - _Requirements: 7.4, 8, NFR 3_

- [x] 3. Identity — use cases + auth API + bootstrap admin
  - Use cases (identity.application): `RegisterLearner` (reports every invalid
    field at once; unknown languages rejected; duplicate email 409),
    `Login` (same 401 for wrong email/password/disabled; dummy BCrypt check so
    timing doesn't reveal which emails exist), `RefreshSession` (rotation via
    `RefreshTokenRotation`; `noRollbackFor = UnauthenticatedException` so a
    reuse revocation is committed even though the request gets 401),
    `Logout` (idempotent), `GetMe`, `BootstrapAdmin` (never promotes an
    existing account). Ports `AuthSettings`; `java.time.Clock` bean.
  - `AuthController` under `/api/v1/auth`: register (201), login, refresh
    (failure also clears the cookie), logout (204), me. Refresh cookie
    `bandup_refresh`: HttpOnly, Secure, SameSite=Strict, Path=/api/v1/auth,
    14 days. The refresh token is never in a JSON body.
  - Sign-in endpoints ignore the `Authorization` header, so an expired access
    token can't block the refresh. First admin from `BANDUP_ADMIN_EMAIL` /
    `BANDUP_ADMIN_PASSWORD` at startup. `LanguageFallbacks` gained
    `isKnown` / `storedSpelling`.
  - Decisions within the spec: register doesn't sign in automatically (the
    UI calls login next); `me`/login return id, display name, roles and
    languages but not the email (requirement 8.6); blank languages default to
    English.
  - _Verified_: `AuthApiTest` (12, real endpoints, Testcontainers, no
    test-wide transaction): register → login → me; all field errors at once;
    duplicate email 409; wrong email = wrong password = malformed email
    (identical 401 body); disabled user can't log in or refresh; cookie flags
    and Max-Age, only the hash stored; refresh rotates; reusing an old token
    ends the whole session and the revocation is committed; no/garbage
    cookie 401; expired access token doesn't block refresh; logout clears the
    cookie and is idempotent; me needs an active user. `BootstrapAdminTest`
    (2): admin created at startup once, can sign in. `SecurityRulesTest`
    probes now need an `X-Security-Probe` header so they never clash with real
    controllers. Live app: full flow over HTTP, test accounts removed
    afterwards. 192 tests green.
  - _Open — needs your decision_: two tabs loading at the same moment send the
    same refresh token; the second looks like reuse and ends the session.
    Proposed fix: a short grace period (e.g. 10 s) in `RefreshTokenRotation`.
  - _Requirements: 8, NFR 2_

---

## Fixes after review

- [x] 4. Identity — bug fixes (found in a review before starting Practice)
  - **Refresh race:** concurrent refreshes of one token all succeeded (8 of 8
    in the test) and forked the session, bypassing reuse detection. Now the
    token row is locked for the transaction (`RefreshTokenRepository.lockByHash`,
    `SELECT … FOR UPDATE` without outer fetch joins): one refresh rotates, the
    others see the token as already used.
  - **Disabled user's refresh:** rotated first (creating a new token) and then
    revoked only two tokens. Now the user is checked before rotating; an
    inactive user's whole session family is revoked and nothing new is issued.
  - **BCrypt inside transactions:** `Login`, `RegisterLearner` and
    `BootstrapAdmin` held a DB connection during the ~250 ms hash. They now have
    no use-case-wide transaction; each read/write runs in its own short
    transaction in the adapters (each writes at most one thing; a late
    duplicate email is still caught by the unique constraint).
  - **Found while fixing — Hibernate proxies:** the adapters read and *wrote*
    entity fields directly; on a lazy proxy, reads return null and writes are
    silently lost. Both identity adapters now unproxy every entity before
    touching its fields.
  - _Verified_: `RefreshSessionBugsTest` (race: at most 1 of 8 concurrent
    refreshes succeeds and the session isn't forked; disabled user: no new
    token, all revoked) and `PasswordHashingOutsideTransactionsTest` (a
    recording hasher proves no transaction is active during hash/verify for
    register, login incl. unknown email, bootstrap). All 5 failed on the old
    code and pass now; the race test passed 5/5 repeated runs. 197 tests green.
  - _Still open (need a decision)_: registration reveals existing emails (409);
    no login rate limiting; `refresh_token` never cleaned up; two tabs
    refreshing at once end the session (the new lock makes this deterministic).

- [x] 5. Identity — design refactor (same review)
  - **Packages:** `identity.application.port` (PasswordHasher, AccessTokenIssuer,
    TokenGenerator, AuthSettings); `identity.infrastructure.{security, config,
    persistence}`; `shared.infrastructure.security` (JWT config, security
    config, CurrentUser adapter). Tests moved with their classes.
  - **Access rules owned by each context:** new `shared.infrastructure.security.ApiAccessRules`
    extension point. `IdentityAccessRules`, `PracticeAccessRules`,
    `ContentAccessRules` declare their own URLs (and Identity its token-free
    sign-in endpoints); `SecurityConfig` only applies them and denies any other
    `/api/**`. The shared kernel contains no context URLs. Practice and
    Content got only this one class each.
  - **JWT claim names defined once:** `AccessTokenClaims` (`roles`, `lang`),
    used by the issuer, the authority converter and `CurrentUser`.
  - **No duplicated session logic:** `SessionIssuer` builds the signed-in
    result for `Login` (new family) and `RefreshSession` (rotated token).
  - **Time only from the Clock:** `JpaUserRepository` uses the injected
    `Clock`; `SignedIn` carries `issuedAt`, so the web layer computes
    `expiresIn` and the cookie's Max-Age without reading the time.
  - _Verified_: behaviour unchanged — all 197 tests green, incl. the security
    rules, token-free refresh and the bug regressions; persistence test now
    uses a fixed clock and asserts `user_credential.updated_at` comes from it;
    no context URLs in `shared`, no `Instant.now()` and no unused imports in
    main code; ArchUnit passes.

- [x] 6. Tactical design — one module per aggregate
  - `identity.domain` was flat (aggregates, value objects, repositories and
    the domain service mixed), so aggregate boundaries were invisible and not
    enforced. Now:
    - `identity.domain.user` — **User** aggregate: `User` (root), `UserId`,
      `Email`, `NewPassword`, `PasswordHash`, `Role`, `UserStatus`,
      `UserRepository`.
    - `identity.domain.session` — **Session** aggregate (a login session as a
      family of refresh tokens): `RefreshToken` (root), `TokenHash`,
      `FamilyId`, `RefreshTokenRotation` (domain service),
      `RefreshTokenRepository`. A token's state only changes through the
      domain service (package-private mutators, now actually enforced by the
      package). References User only by `UserId`.
    - Shared kernel the same way: `shared.domain.language` and
      `shared.domain.error`.
  - Each module has a `package-info.java` naming its root, value objects,
    repository and domain service; the domain-layer `package-info.java` of
    every context states the module-per-aggregate rule.
  - New ArchUnit rules: (7) no class directly in a context's `domain` package;
    (8) aggregate modules of a context use each other only through `*Id` types
    (shared-kernel topics exempt); (9) at most one repository per aggregate
    module, and it's an interface (port).
  - _Verified_: 200 tests green. Probe run: a flat domain class, a session
    class holding a `User`, and a second repository in `user` were each caught
    by exactly their rule; a session class holding only a `UserId` passed.
    Probes deleted.

- [x] 7. Tactical design — building blocks and the Session aggregate
  - **Building-block markers** (plain Java, `shared.domain.ddd`):
    `@AggregateRoot`, `@Entity`, `@ValueObject`, `@DomainService`, on every
    domain class. `User`: root; `UserId`, `Email`, `NewPassword`,
    `PasswordHash`, `Role`, `UserStatus`: value objects. Shared kernel:
    `LanguageCode`, `LanguageChain`, `Translations`, `Violation`: value
    objects; `LanguageFallbacks`: domain service.
  - **Session aggregate** (replaces "each RefreshToken is a root" +
    `RefreshTokenRotation` + `FamilyId` + `RefreshTokenRepository`):
    `Session` (root, `SessionId` = `family_id`) holds its `RefreshToken`
    entities and owns the rules: `present()` (rotate / reuse → revoke all /
    expired / revoked), `revoke()`; invariants checked on restore (non-empty,
    no duplicate tokens, successors inside the session). `RefreshToken` is an
    `@Entity` with package-private mutators. `SessionRepository` loads and
    saves whole sessions; `JpaSessionRepository` locks every row of the
    session (locking twice: a waiting FOR UPDATE can't see rows inserted
    meanwhile — found by the new invariant). No schema change.
  - **Identity-based equality:** `User` (by `UserId`), `Session` (by
    `SessionId`), `RefreshToken` (by `TokenHash`).
  - Use cases now load → ask the aggregate → save: `SessionIssuer`,
    `RefreshSession`, `Logout`.
  - New ArchUnit rules: (10) every domain class declares its building block;
    (11) exactly one `@AggregateRoot` per aggregate module; (12) a repository
    works with its module's root, never an inner `@Entity`. Rule 8 tightened:
    the referenced id must be a `@ValueObject`.
  - _Verified_: `SessionTest` (12: rotation, reuse revokes all, expired,
    revoked, idempotent revoke, foreign token, invariants, immutability,
    equality), `RefreshTokenTest` (7), entity equality for `User`,
    persistence (whole-session round-trip found by any token, reuse revokes
    the stored session), new race test "thief replays old token while user
    refreshes new one" (10 rounds, no surviving token). Both concurrency tests
    passed 5/5 repeated runs. Probe run for rules 10–12: each caught exactly
    its violation. 211 tests green; API behaviour unchanged.

- [x] 8. Tactical design — building-block packages inside each aggregate
  - Layout chosen by the user ("aggregate, then blocks"):
    `user/{aggregate: User; valueobject: UserId, Email, NewPassword,
    PasswordHash, Role, UserStatus; repository: UserRepository}` and
    `session/{aggregate: Session; entity: RefreshToken; valueobject:
    SessionId, TokenHash; repository: SessionRepository}`. Tests moved
    alongside. Each package has a `package-info.java`.
  - Root and entity now live in different Java packages, so package-private
    could no longer protect the entity. `RefreshToken` became **immutable**:
    `withReplacedBy` / `revoked` return copies and only `Session` swaps them
    into its token list, so the aggregate's rules still can't be bypassed.
  - New ArchUnit rule (13): inside an aggregate module every class lives in the
    package of its building block (`aggregate`, `entity`, `valueobject`,
    `repository`, `service`, `exception`), nothing loose in the module folder.
  - _Verified_: 212 tests green (incl. a new "copies never change the
    original" test); probe run: a value object in `aggregate/` and a loose
    class in `user/` were both caught by rule 13 only; no unused imports.

- [x] 9. Fix: display name checked with the other registration fields
  - Found by the sign-up page in Chrome (frontend task 3): a too-long display
    name was only checked inside `User.registerLearner`, after the other
    checks and after BCrypt, so it wasn't reported together with them
    (requirement 8.1). `User.displayName(raw)` (domain rule, public) is now
    collected by `RegisterLearner` with email, password and languages.
  - _Verified_: regression test failed first, then passed;
    `AuthApiTest` 13 green.

- [x] 10. Fix: leaving a page mid-refresh signed the user out
  - Found by a Selenium scenario (1 in 10 in an experiment): the browser
    dropped the reply to a refresh, sent the replaced token again, and reuse
    detection ended the session. `Session.present`: a replaced token within
    `REUSE_GRACE` (30 s) whose successor is unused rotates again; otherwise
    still theft. Approved by the user.
  - _Verified_: 3 domain tests first (the grace one failed), persistence
    test of a grace rotation, API test "a refresh whose reply was lost can be
    repeated"; theft tests now replay after the real user moved on or after
    the window (`AuthApiTest`, `IdentityPersistenceTest`,
    `RefreshSessionBugsTest`: 8 copies at once → ≤ 2 through, chain never
    forks, session ends).

- [x] 11. Login limits (requirement 8.12)
  - Migration V6 `login_throttle`; module `domain.throttle`
    (`LoginThrottle`, `ThrottleKey`, `ThrottlePolicy`,
    `LoginThrottleRepository`); `LoginThrottling`; `JdbcLoginThrottleRepository`;
    `TooManyRequestsException` → 429 + `Retry-After`; `Login.Command` carries
    the client address; the sign-in page shows the 429 message.
  - _Verified_: `LoginThrottleTest` (4); `AuthApiTest` +4: 5 wrong → even the
    right password gets 429 (Retry-After ≈ 900, message), other emails fine;
    an email without an account locks the same way; success clears the count;
    30 failures from one address lock it, another address is fine. Mutation
    check: without the check 3 tests fail.

- [x] 12. Nightly clean-up (requirement 8.13)
  - `PurgeEndedSessions` + `SessionCleanupSchedule` (`@EnableScheduling`);
    `SessionRepository.deleteEndedBefore`, `LoginThrottleRepository.deleteIdleSince`.
  - **Bug found by the test before it shipped:** `bool_and` skips NULL, so a
    never-revoked token didn't count and live sessions would have been
    deleted; fixed with `coalesce`.
  - _Verified_: `PurgeEndedSessionsTest`: live and just-revoked sessions
    kept, long-expired and long-revoked ones deleted whole; idle counter
    deleted, active lock and recent counter kept.

- [x] 13. Local admin login: `admin@a.az` / `admin`
  - Asked by the user: no settings to look up for local runs. `local`
    profile is the default (`spring.profiles.default: local`);
    `application-local.yaml` sets the admin and
    `bandup.auth.allow-short-admin-password: true`;
    `BootstrapAdmin.ensureAdmin(…, localDevelopment)` uses
    `NewPassword.forLocalDevelopment` (non-blank, ≤ 72 bytes). Every other
    password keeps the 10-character rule. Tests and `uiTest` run with the
    `test` profile.
  - The existing dev database admin (`admin@bandup.local`) was changed to
    `admin@a.az` / `admin` directly (BCrypt hash made with the project's
    Spring Security).
  - _Verified_: `NewPasswordTest` +1, `BootstrapAdminTest` +1 (short password
    refused unless local); the real app on the dev database: login
    `admin@a.az` / `admin` → 200. 419 tests green.
