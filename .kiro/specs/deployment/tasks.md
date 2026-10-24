# Deployment — tasks

- [x] 1. Production profile `application-prod.yaml` (requirements 3–5).
- [x] 2. `bandup.jwt.require-secret`: missing / short secret stops the start
  (`JwtSecretRequiredTest`).
- [x] 3. Security headers in `SecurityConfig` (requirement 6); home page
  inline `style` attributes replaced by classes so the CSP needs no
  `unsafe-inline` (`SecurityHeadersTest`; all browser tests run under the
  CSP).
- [x] 4. `Dockerfile` + `.dockerignore` (requirement 8).
- [x] 5. `deploy/compose.yaml`, `Caddyfile`, `.env.example`; `.gitignore`
  covers `deploy/.env` and `deploy/backups/` (requirements 1–3).
- [x] 6. `deploy/backup.sh` and `deploy/README.md` (requirement 7).
- _Verified_: `ProductionProfileTest` starts the app with the prod profile;
  the image was built and the whole stack started locally with
  `BANDUP_DOMAIN=localhost`: Flyway to v7, first admin created, http → https
  redirect, headers incl. HSTS (forwarded HTTPS seen), sign-in with a
  `Secure; HttpOnly; SameSite=Strict` cookie, cover upload and download, app
  port not published, the image survived a new app container, `backup.sh`
  produced both files and the dump restored into a fresh database (1
  exercise, 1 image, 1 user, v7). Test stack removed afterwards.

## Open (user)

- Buy the domain, rent the VPS, follow `deploy/README.md`.
- Off-server copy of backups; uptime monitoring; email provider.
