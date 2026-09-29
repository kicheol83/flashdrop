# FlashDrop — First-come, first-served coupon issuance

**English** | [한국어](./README.md) | [O'zbekcha](./README.uz.md)

50 coupons, hundreds of concurrent requests, and not a single coupon may be over-issued. The same business rule is implemented with four different concurrency-control strategies and compared with k6.

**Live Demo:** https://flashdrop.javohir.dev

## Four approaches

| # | Strategy | Endpoint | Package |
|---|---|---|---|
| 1 | No lock (deliberately broken, oversells) | `POST /api/v1/nolock/campaigns/{code}/claim` | `strategy.nolock` |
| 2 | `SELECT ... FOR UPDATE` | `POST /api/v1/forupdate/campaigns/{code}/claim` | `strategy.forupdate` |
| 3 | Optimistic lock (`@Version` + retry) | `POST /api/v1/optimistic/campaigns/{code}/claim` | `strategy.optimistic` |
| 4 | Redis atomic decrement + async write | `POST /api/v1/redis/campaigns/{code}/claim` | `strategy.redis` |

Each strategy is isolated with its own tables, entities and endpoint, so k6 results never mix.

## How each strategy works

**1 — No lock.** `findByCode` → read `remainingQuantity` → check → decrement by one and save. No `@Transactional`, no lock. Two requests read the same value and both decide "in stock" — the classic *lost update*. The test proves it with `successCount > 50` or `remaining_quantity < 0`.

**2 — SELECT FOR UPDATE.** `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` locks the row as soon as it is read; other transactions queue on that row. Correct, but under load the lock contention lowers throughput, which shows up in the k6 numbers.

**3 — Optimistic lock.** Nothing is locked; only the `version` column checks "has this changed since I read it". On a conflict, `ObjectOptimisticLockingFailureException` is thrown and the attempt is retried (`OptimisticClaimService`, up to 20 retries). Key point: the retry logic and the transactional logic are deliberately split into two beans (`OptimisticClaimService` → `OptimisticClaimAttempt`), because Spring's `@Transactional` proxy does not intercept **self-invocation** (one method calling another inside the same bean). With everything in one class, the transaction would silently not apply.

**4 — Redis.** Stock lives in Redis (`DECR`) and so does deduplication (`SADD` on a set of users who already claimed). Both run atomically in a single Lua script (`claim.lua`), so there is no check-then-act problem at all. The Postgres write happens in the background via `@Async` (`RedisClaimWriteBackService`): the response returns immediately and the DB record follows. The trade-off: if the application dies after Redis but before the DB write, a coupon counted as issued in Redis may be missing in Postgres.

Every strategy returns `CAMPAIGN_NOT_STARTED` / `ALREADY_CLAIMED` / `SOLD_OUT` / `SUCCESS` through the same `ClaimResult` (`common` package). Idempotency is guaranteed in all strategies: 1–3 via a DB-level `UNIQUE(campaign_id, user_id)`, 4 via a Redis SET plus the same unique constraint in the DB as a safety net.

## Stack

Java 21 (virtual threads in tests) · Spring Boot 3.5 · PostgreSQL 17 · Redis · Flyway · Testcontainers · k6

Tomcat (`accept-count`, `threads.max`) and HikariCP (`maximum-pool-size: 50`) are configured above their defaults in `application.yml`. k6 testing showed that the defaults (10 connections, a small backlog) produce artificial "connection refused" errors and queueing delays at 300 concurrent requests.

## Running locally (PowerShell)

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

Or open the folder in IntelliJ IDEA and let Gradle sync automatically.

## Creating demo campaigns

For strategies 1–3:

```powershell
Get-Content scripts/seed-demo-campaigns.sql | docker exec -i flashdrop-postgres-1 psql -U flashdrop -d flashdrop
```

(Check the container name with `docker ps`; `docker compose up -d` usually names it `flashdrop-postgres-1`.)

For the Redis strategy, once the server is running:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/redis/campaigns `
  -ContentType "application/json" `
  -Body '{"code":"FLASH50-REDIS","totalQuantity":50,"startsAt":"2026-01-01T00:00:00"}'
```

## Running the tests

```powershell
.\gradlew.bat test
```

Each strategy has a Testcontainers-based concurrency test (`*ConcurrencyTest`) in which 300 virtual threads hit 50 coupons at the same time:

- `NoLockConcurrencyTest` — **expected to fail**: it proves the oversell (`successCount` exceeds 50 or `remaining_quantity` goes negative)
- `ForUpdateConcurrencyTest`, `OptimisticConcurrencyTest`, `RedisConcurrencyTest` — **expected to pass**: exactly 50 successful claims, no oversell

## Rate limiting

The `/claim` endpoint of all four strategies is protected by one shared interceptor (`com.flashdrop.ratelimit`); every request matching `/api/v1/*/campaigns/*/claim` goes through it.

- **Algorithm:** token bucket (Bucket4j) with state stored in Redis (via Lettuce), so the limit is shared even when the application runs as several instances.
- **Key:** `userId`. This protection is weak — `userId` is supplied by the client and can be changed at will; in production it should be combined with the IP address.
- **Default limit:** 5 requests per 10 seconds per `userId` (`application.yml` → `flashdrop.rate-limit.*`).
- **Response:** when the limit is exceeded, **429 Too Many Requests** instead of 200, with body `{"status":"RATE_LIMITED","claimId":null}` and the `Retry-After` and `X-Rate-Limit-Remaining` headers. This deliberately differs from every other outcome (SUCCESS/SOLD_OUT/...) returning 200: it is an HTTP-level rejection, not a business result.
- **The Redis connection is `@Lazy`:** the `RateLimitConfig` bean connects on the first real HTTP request, not when the context starts, so the existing `*ConcurrencyTest`s, which call services directly, run without Redis.
- **Why the 300-VU k6 tests are not affected:** each VU uses a different `userId` (`k6-user-{VU}-{ITER}`) and asks only once; the limit only stops the **same** `userId` retrying repeatedly.

To see it work:

```powershell
k6 run k6/rate-limit-check.js
```

It sends 12 requests with one `userId`, 0.2 s apart — the first 5 pass and the remaining 7 should return `429`.

## Load testing with k6

```powershell
k6 run k6/nolock.js
k6 run k6/forupdate.js
k6 run k6/optimistic.js
k6 run k6/redis.js
```

Each runs 300 VUs and 300 iterations against the `FLASH50` (or `FLASH50-REDIS`) campaign. `redis.js` creates and syncs its campaign in `setup()`; for the others, run the seed script above first.

## Results (k6, 300 concurrent requests, 50 coupons, local Windows/Docker Desktop)

| Strategy | Throughput | avg latency | p95 latency | Success / Sold out | Oversell? |
|---|---|---|---|---|---|
| No lock | 80.4 req/s | 2.89s | 3.58s | 300 / 0 (all "SUCCESS"!) | **YES — 6x oversell (300 instead of 50)** |
| SELECT FOR UPDATE | 156.4 req/s | 1.06s | 1.74s | 50 / 250 | No |
| Optimistic lock | 114.1 req/s | 2.2s | 2.53s | 50 / 250 | No |
| Redis atomic | 240.8 req/s | 390ms | 582ms | 50 / 250 | No |

**Why is the no-lock strategy the slowest, with the lowest throughput, despite having "no lock"?** Because it never knows when to stop — all 300 requests perform the full write (UPDATE + INSERT), and none exits early with a cheap "SOLD_OUT". The other three reject the remaining 250 almost for free after the first 50, and that difference drives both throughput and latency.

**A fair comparison of the other three** (each did exactly 50 writes + 250 cheap rejections):
- **Redis is fastest** — the Lua script runs entirely in Redis memory, and the hot path never touches Postgres (the write is `@Async` in the background).
- **SELECT FOR UPDATE is second** — there is lock waiting, but it is a clean single queue with no retries.
- **Optimistic lock is the slowest of the three** — when 300 threads hit the same row at once, conflicts are frequent and every failed attempt must go back to the database from scratch (a retry storm). It is the classic example of optimistic locking being fast under low contention and slower than pessimistic locking under high contention.

## Frontend — live console

`frontend/index.html` is a single standalone HTML file (no build, no dependencies). It has two columns: on the left, the claim panel (strategy, campaign code, user ID, claim button); on the right, a live board (remaining quantity read from the server every 1.2 s, per-outcome counters for the current session, and a live log of the last 60 attempts).

In production the page and the API are served from the same domain, so the "server address" field is set to the current origin automatically. Locally, opening the file directly in a browser uses the default `http://localhost:8080`.

Two things were added to the backend for it:
- **`GET /api/v1/{strategy}/campaigns/{code}`** — one per strategy (four in total), returning remaining/total quantity. For the Redis strategy it reads the live `stock` key from Redis rather than Postgres.
- **CORS** (`com.flashdrop.web.CorsConfig`) — open for `/api/**` so the file can be opened locally via `file://`.

When the Redis strategy is selected, an extra "sync Redis (init)" button appears so the campaign can be created or reset straight from the page.

## Production deployment

```bash
cp .env.prod.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

- `app`: the Spring Boot application built by a multi-stage `Dockerfile`, running as a non-root user with a 1 GB memory limit.
- `web`: Caddy serves `frontend/` as static files and proxies `/api/*` to the application.
- `seed`: creates the four demo campaigns on start. Run `docker compose -f docker-compose.prod.yml run --rm seed` at any time to reset the demo.
- Postgres and Redis ports are never published; generate `POSTGRES_PASSWORD` with `openssl rand -hex 24`.

## Next steps

- `campaign_redis.remaining_quantity` is only written at init and is not synced with Redis afterwards — a reconciliation job is needed to bring Postgres in line eventually.
- The frontend counters are per browser session (they reset on refresh); for several people watching at once, the backend would need a shared metrics endpoint.
