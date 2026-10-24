# Deploying BandUp on a VPS

One Linux server runs everything with Docker Compose:

| Container | What it does | Reachable from the internet |
|---|---|---|
| `caddy` | HTTPS (free Let's Encrypt certificate, renewed automatically), forwards to the app | yes: ports 80, 443 |
| `app` | BandUp (Spring Boot, profile `prod`) | no |
| `db` | PostgreSQL 16 | no |

Data lives in Docker volumes: `db-data` (database), `media` (uploaded cover
images), `caddy-data` (certificates). They survive restarts and new versions.

## 1. Server

- Ubuntu 24.04, 2 GB RAM or more (Hetzner CX22, DigitalOcean 2 GB…).
- Install Docker: <https://docs.docker.com/engine/install/ubuntu/>
- Firewall: allow only SSH, 80 and 443.

  ```sh
  ufw allow OpenSSH && ufw allow 80 && ufw allow 443 && ufw enable
  ```

## 2. Domain

Buy a domain and add a DNS **A record** pointing to the server's IP address.
Wait until `ping your-domain` shows that IP — Caddy needs it to get the
certificate.

## 3. First start

```sh
git clone <your repository> /opt/bandup
cd /opt/bandup/deploy
cp .env.example .env
nano .env            # domain, passwords, secrets (openssl rand -base64 48)
chmod 600 .env
docker compose up -d --build
docker compose logs -f app     # wait for "Started BandupApplication"
```

Open `https://your-domain`. Sign in with `BANDUP_ADMIN_EMAIL` /
`BANDUP_ADMIN_PASSWORD` and go to `https://your-domain/admin`.

The app refuses to start without `BANDUP_JWT_SECRET` (32+ characters), and
the admin password needs 10+ characters.

## 4. Backups (do this on day one)

```sh
crontab -e
# add:
30 3 * * * cd /opt/bandup/deploy && ./backup.sh >> backups/backup.log 2>&1
```

Every night: the database (`db_*.dump`) and the images (`media_*.tar.gz`) go
to `deploy/backups/`, kept for 14 days. **Also copy them off the server**
(e.g. `rsync` to another machine, or your provider's backup/snapshot
feature) — a backup on the same disk is lost with the server.

### Restore

```sh
cd /opt/bandup/deploy
docker compose stop app
docker compose exec -T db dropdb -U bandup bandup
docker compose exec -T db createdb -U bandup bandup
docker compose exec -T db pg_restore -U bandup -d bandup --no-owner < backups/db_<date>.dump
docker compose run --rm --no-deps -T --entrypoint tar -v "$(pwd)/backups:/backups" app \
    -xzf /backups/media_<date>.tar.gz -C /app/data
docker compose start app
```

## 5. New version

```sh
cd /opt/bandup && git pull
cd deploy && docker compose up -d --build app
```

Database changes (Flyway migrations) run by themselves when the app starts.
Take a backup first (`./backup.sh`).

## 6. Day to day

| Task | Command (in `deploy/`) |
|---|---|
| Logs | `docker compose logs -f app` (or `caddy`, `db`) |
| Status | `docker compose ps` |
| Restart the app | `docker compose restart app` |
| Database shell | `docker compose exec db psql -U bandup bandup` |

## Not set up yet

- Email (needed for "forgot password").
- Monitoring / alerts when the site is down (e.g. a free uptime checker
  pointed at `https://your-domain/`).
