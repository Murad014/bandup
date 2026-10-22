# Tasks — Backend · Identity context

Users, roles, email/password login, JWT access tokens, rotating refresh tokens.
**[Backend]** only. **Nothing is started.** Each task is done only when
requested, one at a time, and ends green (`sh gradlew test`).

- **Depends on:** foundation tasks 1–3 in `../tasks.md` (architecture, shared
  kernel); `database` task 4 (V3 login tables, done).
- **References:** requirements and design sections refer to
  `../requirements.md` and `../design.md`.

---

- [ ] 1. Identity — domain
  - `User`, `Email`, `PasswordHash`, `Role`, `UserStatus`, `RefreshToken`
    (rotation, reuse detection, family revoke), repository ports. JDK only.
  - _Verify_: unit tests: email validation/lower-casing, password length rule,
    `canAuthenticate` per status, rotate → old unusable, reuse → family
    revoked, expiry.
  - _Requirements: 8_

- [ ] 2. Identity — adapters + security configuration
  - Uses the login tables from `database` task 4 (V3, done).
  - JPA adapters for users/roles/credentials/refresh tokens;
    `BCryptPasswordHasher`; `JwtAccessTokenIssuer` (HS256,
    `BANDUP_JWT_SECRET`); `SecureRandomTokenGenerator`; Spring Security
    config (stateless, resource server, rules from design §6.4).
  - _Verify_: Testcontainers round-trips; JWT issued → decoded with the right
    claims; expired/tampered token → 401; role rules → 401/403 per endpoint
    group.
  - _Requirements: 7.4, 8, NFR 3_

- [ ] 3. Identity — use cases + auth API + bootstrap admin
  - `RegisterLearner`, `Login`, `RefreshSession`, `Logout`, `GetMe`,
    `BootstrapAdmin`; `AuthController` with the refresh cookie.
  - The old dev guard (`AdminAccess`, `AdminAccessFilter`,
    `app.admin.dev-allow`) is already removed; admin data is protected only
    by the API's role rules.
  - _Verify_: MockMvc + Testcontainers: register → login → me; wrong email
    and wrong password give the same 401; refresh rotates the cookie; reusing
    an old refresh token revokes the family; logout clears the cookie;
    disabled user can't log in; bootstrap creates the admin once; cookie
    flags (HttpOnly, Secure, SameSite=Strict, Path).
  - _Requirements: 8, NFR 2_
