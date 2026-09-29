#!/bin/sh
set -eu

until psql -tAc "SELECT to_regclass('public.claim_redis')" | grep -q claim_redis; do
  sleep 2
done

psql -v ON_ERROR_STOP=1 -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements"
psql -v ON_ERROR_STOP=1 -f /seed/seed-demo-campaigns.sql

until wget -q -O /dev/null \
  --header "Content-Type: application/json" \
  --post-data '{"code":"FLASH50-REDIS","totalQuantity":50,"startsAt":"2026-01-01T00:00:00"}' \
  http://app:8080/api/v1/redis/campaigns; do
  sleep 2
done

echo "demo campaigns reset"
