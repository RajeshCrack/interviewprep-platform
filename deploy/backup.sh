#!/usr/bin/env bash
# Nightly PostgreSQL backup for the API server. Run from the repo's deploy/ folder, for example by cron:
#   30 2 * * * cd /opt/gateready/deploy && ./backup.sh >> /var/log/gateready-backup.log 2>&1
# Keeps 14 days. Copy /var/backups/gateready off the server too (Hetzner Storage Box, S3, anything), or a lost
# server takes the backups with it.
set -euo pipefail
DIR=/var/backups/gateready
mkdir -p "$DIR"
FILE="$DIR/gateready-$(date +%F-%H%M).sql.gz"
docker compose exec -T db pg_dump -U gateready gateready | gzip > "$FILE"
test -s "$FILE"                      # fail loudly if the dump is empty
find "$DIR" -name 'gateready-*.sql.gz' -mtime +14 -delete
echo "backup ok: $FILE ($(du -h "$FILE" | cut -f1))"
