# Deployment — requirements

Hosting (user choice): one VPS with Docker; no domain yet (`BANDUP_DOMAIN`).

1. THE site SHALL be served only over HTTPS with an automatically renewed
   certificate; plain HTTP SHALL redirect to HTTPS.
2. Only the HTTPS proxy SHALL be reachable from the internet; the app and
   the database SHALL NOT publish ports.
3. Secrets (database password, `BANDUP_JWT_SECRET`, first admin) SHALL come
   from an environment file on the server that is never committed. WHEN the
   JWT secret is missing or shorter than 32 bytes in production THEN the app
   SHALL refuse to start.
4. The production profile SHALL NOT allow the local shortcuts (short admin
   password, random JWT key) and SHALL NOT show stack traces or exception
   messages in error responses.
5. The app SHALL see the visitor's real address and HTTPS from the proxy's
   forwarded headers (login limits, HSTS, Secure cookie).
6. Every response SHALL carry browser protections: Content-Security-Policy
   (own scripts/styles/images only, plus Google Fonts; no inline scripts or
   style attributes; no framing), X-Frame-Options DENY, nosniff,
   Referrer-Policy, Permissions-Policy, and HSTS on HTTPS.
7. The database and uploaded images SHALL survive restarts and new
   versions, and SHALL be backed up nightly (kept 14 days) with a documented
   restore.
8. The app SHALL run as a non-root user.
