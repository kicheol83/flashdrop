#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.prod.yml)
HISTORY=.deploy-history

git pull --ff-only
sha="$(git rev-parse --short HEAD)"

"${COMPOSE[@]}" build app
docker tag flashdrop-app:latest "flashdrop-app:$sha"
"${COMPOSE[@]}" up -d app web

echo "$sha" >> "$HISTORY"
echo "deployed $sha"
