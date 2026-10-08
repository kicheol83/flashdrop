#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.prod.yml)
HISTORY=.deploy-history

git fetch --quiet
if git grep -qE '^(<<<<<<<|>>>>>>>)( |$)' '@{u}' -- .; then
  echo "merge conflict markers found in upstream, deploy aborted" >&2
  git grep -lE '^(<<<<<<<|>>>>>>>)( |$)' '@{u}' -- . >&2
  exit 1
fi
git merge --ff-only '@{u}'
sha="$(git rev-parse --short HEAD)"

"${COMPOSE[@]}" build app
docker tag flashdrop-app:latest "flashdrop-app:$sha"
"${COMPOSE[@]}" up -d app web

echo "$sha" >> "$HISTORY"
echo "deployed $sha"
