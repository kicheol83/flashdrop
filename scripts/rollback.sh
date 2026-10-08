#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.prod.yml)
HISTORY=.deploy-history

if [ -n "${1:-}" ]; then
  target="$1"
else
  if [ "$(wc -l < "$HISTORY")" -lt 2 ]; then
    echo "no previous deploy in $HISTORY" >&2
    exit 1
  fi
  target="$(tail -n 2 "$HISTORY" | head -n 1)"
fi

docker image inspect "flashdrop-app:$target" > /dev/null
docker tag "flashdrop-app:$target" flashdrop-app:latest
"${COMPOSE[@]}" up -d --no-build --force-recreate app

echo "$target" >> "$HISTORY"
echo "rolled back to $target"