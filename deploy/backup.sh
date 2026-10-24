#!/bin/sh
# Nightly backup: the database and the uploaded images, kept for 14 days.
# Run on the server from this folder; cron example (3:30 every night):
#   30 3 * * * cd /opt/bandup/deploy && ./backup.sh >> backups/backup.log 2>&1
# Copy the backups folder to another machine too (a backup on the same disk
# doesn't survive losing the server).
set -eu
cd "$(dirname "$0")"
mkdir -p backups
stamp=$(date +%Y-%m-%d_%H%M)

docker compose exec -T db pg_dump -U bandup -d bandup --format=custom > "backups/db_$stamp.dump"
docker compose run --rm --no-deps -T --entrypoint tar -v "$(pwd)/backups:/backups" app \
    -czf "/backups/media_$stamp.tar.gz" -C /app/data media

find backups -name 'db_*.dump' -mtime +14 -delete
find backups -name 'media_*.tar.gz' -mtime +14 -delete
echo "$(date '+%F %T') backup done: db_$stamp.dump media_$stamp.tar.gz"
