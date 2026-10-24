# Deployment — design

```
internet ──443/80──▶ caddy ──http──▶ app:8080 ──▶ db:5432
                     (TLS, gzip)     (prod profile)  (PostgreSQL 16)
                                     /app/data/media ◀── volume "media"
```

| Piece | File | Notes |
|---|---|---|
| Image | `Dockerfile` | JDK 21 builds `bootJar`; JRE 21 runs it as user `bandup`; `SPRING_PROFILES_ACTIVE=prod`; `MaxRAMPercentage=75` |
| Stack | `deploy/compose.yaml` | `db` (healthcheck), `app` (no ports), `caddy` (80/443); volumes `db-data`, `media`, `caddy-data`, `caddy-config`; missing env values stop `compose up` |
| HTTPS | `deploy/Caddyfile` | `{$BANDUP_DOMAIN}` → `app:8080`, request body ≤ 5 MB |
| Settings | `application-prod.yaml` | datasource / JWT / admin from env, `require-secret: true`, `forward-headers-strategy: native` (Tomcat trusts private-network proxies only), no stack traces, compression, graceful shutdown, media in `/app/data/media` |
| Headers | `SecurityConfig` | CSP (`CONTENT_SECURITY_POLICY`), frame DENY, referrer, permissions; HSTS by Spring Security on secure requests |
| Secrets | `deploy/.env` (from `.env.example`) | git-ignored, `chmod 600` |
| Backups | `deploy/backup.sh` + cron | `pg_dump --format=custom` + tar of the media volume; 14 days; copy off-server |
| Runbook | `deploy/README.md` | server, DNS, first start, backups, restore, updates |

Decisions: Caddy rather than nginx + certbot (certificates without extra
setup); images on a volume (one server; object storage when there are
several app copies — the `ImageStore` port allows it); no Spring Actuator
(no new dependency; Compose checks the database, Caddy reports the app).
