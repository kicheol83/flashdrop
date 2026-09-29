# FlashDrop — 선착순 쿠폰 발급 시스템

[English](./README.en.md) | **한국어** | [O'zbekcha](./README.uz.md)

쿠폰 50장, 동시 요청 수백 건, 그리고 단 한 장도 초과 발급되어서는 안 된다는 조건. 같은 비즈니스 규칙을 4가지 동시성 제어 전략으로 구현하고 k6로 비교하는 프로젝트입니다.

**Live Demo:** https://flashdrop.javohir.dev

## 4가지 접근 방식

| # | 전략 | Endpoint | 패키지 |
|---|---|---|---|
| 1 | 락 없음 (의도적으로 잘못된 구현, 초과 발급 발생) | `POST /api/v1/nolock/campaigns/{code}/claim` | `strategy.nolock` |
| 2 | `SELECT ... FOR UPDATE` | `POST /api/v1/forupdate/campaigns/{code}/claim` | `strategy.forupdate` |
| 3 | 낙관적 락 (`@Version` + 재시도) | `POST /api/v1/optimistic/campaigns/{code}/claim` | `strategy.optimistic` |
| 4 | Redis 원자적 차감 + 비동기 저장 | `POST /api/v1/redis/campaigns/{code}/claim` | `strategy.redis` |

각 전략은 자체 테이블, 엔티티, 엔드포인트로 완전히 분리되어 있어 k6 결과가 서로 섞이지 않습니다.

## 전략별 동작 방식

**1 — 락 없음.** `findByCode`로 `remainingQuantity`를 읽고, 확인한 뒤, 1을 줄여 저장합니다. `@Transactional`도 락도 없습니다. 두 요청이 같은 값을 읽고 둘 다 "재고 있음"으로 판단하는 전형적인 *lost update* 문제입니다. 테스트는 `successCount > 50` 또는 `remaining_quantity < 0`으로 이를 증명합니다.

**2 — SELECT FOR UPDATE.** `@Transactional` + `@Lock(PESSIMISTIC_WRITE)`로 행을 읽는 즉시 락을 걸고, 다른 트랜잭션은 해당 행에서 대기합니다. 정확하지만, 부하가 높을수록 락 대기(contention)로 처리량이 떨어지며 이는 k6 수치에서 확인할 수 있습니다.

**3 — 낙관적 락.** 아무것도 잠그지 않고, `version` 컬럼으로 "내가 읽은 이후 변경되지 않았는지"만 확인합니다. 충돌 시 `ObjectOptimisticLockingFailureException`이 발생하고 재시도합니다(`OptimisticClaimService`, 최대 20회). 핵심 포인트: 재시도 로직과 트랜잭션 로직을 의도적으로 두 개의 빈으로 분리했습니다(`OptimisticClaimService` → `OptimisticClaimAttempt`). Spring의 `@Transactional` 프록시는 **self-invocation**(같은 빈 안에서 메서드가 다른 메서드를 호출하는 경우)을 가로채지 못하므로, 한 클래스에 모두 넣으면 트랜잭션이 실제로는 적용되지 않습니다.

**4 — Redis.** 재고는 Redis(`DECR`)에, 중복 방지도 Redis(`SADD`로 발급받은 사용자 집합 관리)에 있으며, 둘 다 하나의 Lua 스크립트(`claim.lua`) 안에서 원자적으로 실행되므로 check-then-act 문제가 원천적으로 없습니다. Postgres 저장은 `@Async`로 백그라운드에서 수행되어(`RedisClaimWriteBackService`) 응답은 즉시 반환되고 DB 기록은 뒤따라옵니다. 대가도 있습니다. Redis 처리 후 DB 저장 전에 애플리케이션이 죽으면, Redis에서는 발급된 쿠폰이 Postgres에는 없을 수 있습니다.

모든 전략은 `CAMPAIGN_NOT_STARTED` / `ALREADY_CLAIMED` / `SOLD_OUT` / `SUCCESS`를 동일한 `ClaimResult`(`common` 패키지)로 반환합니다. 중복 발급 방지는 모든 전략에서 보장됩니다. 1–3번은 DB 레벨의 `UNIQUE(campaign_id, user_id)`, 4번은 Redis SET과 안전장치로 DB의 unique 제약을 함께 사용합니다.

## 기술 스택

Java 21 (테스트에서 virtual thread 사용) · Spring Boot 3.5 · PostgreSQL 17 · Redis · Flyway · Testcontainers · k6

`application.yml`에서 Tomcat(`accept-count`, `threads.max`)과 HikariCP(`maximum-pool-size: 50`) 설정을 기본값보다 크게 잡았습니다. 기본값(커넥션 10개, 작은 backlog)으로는 동시 요청 300건에서 인위적인 "connection refused"와 대기 지연이 발생한다는 것을 k6 테스트로 확인했습니다.

## 로컬 실행 (PowerShell)

```powershell
docker compose up -d
.\gradlew.bat bootRun
```

또는 IntelliJ IDEA에서 폴더를 열면 Gradle이 자동으로 동기화됩니다.

## 데모 캠페인 생성

1–3번 전략용:

```powershell
Get-Content scripts/seed-demo-campaigns.sql | docker exec -i flashdrop-postgres-1 psql -U flashdrop -d flashdrop
```

(컨테이너 이름은 `docker ps`로 확인하세요. `docker compose up -d`는 보통 `flashdrop-postgres-1`로 이름을 붙입니다.)

Redis 전략은 서버 실행 후:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/redis/campaigns `
  -ContentType "application/json" `
  -Body '{"code":"FLASH50-REDIS","totalQuantity":50,"startsAt":"2026-01-01T00:00:00"}'
```

## 테스트 실행

```powershell
.\gradlew.bat test
```

각 전략에는 Testcontainers 기반 동시성 테스트(`*ConcurrencyTest`)가 있으며, 300개의 virtual thread가 동시에 50장의 쿠폰을 요청합니다.

- `NoLockConcurrencyTest`: **실패가 정상**입니다. 초과 발급을 증명합니다(`successCount`가 50을 넘거나 `remaining_quantity`가 음수가 됨).
- `ForUpdateConcurrencyTest`, `OptimisticConcurrencyTest`, `RedisConcurrencyTest`: **성공이 정상**입니다. 정확히 50건만 성공하고 초과 발급은 없습니다.

## Rate Limiting

4가지 전략의 `/claim` 엔드포인트는 하나의 공통 인터셉터(`com.flashdrop.ratelimit`)로 보호됩니다. `/api/v1/*/campaigns/*/claim` 패턴에 맞는 모든 요청이 이 인터셉터를 거칩니다.

- **알고리즘:** 토큰 버킷(Bucket4j). 상태는 Redis(Lettuce)에 저장되므로 애플리케이션이 여러 인스턴스로 실행되어도 제한이 공유됩니다.
- **키:** `userId`. 이 방식은 약합니다. `userId`는 클라이언트가 직접 보내는 값이라 임의로 바꿀 수 있으므로, 운영 환경에서는 IP와 함께 사용해야 합니다.
- **기본 제한:** `userId`당 10초에 5회 (`application.yml` → `flashdrop.rate-limit.*`).
- **응답:** 제한 초과 시 200이 아닌 **429 Too Many Requests**를 반환하며, 본문은 `{"status":"RATE_LIMITED","claimId":null}`, 헤더에 `Retry-After`와 `X-Rate-Limit-Remaining`이 포함됩니다. 다른 모든 결과(SUCCESS/SOLD_OUT 등)가 200을 반환하는 것과 의도적으로 구분했습니다. 이는 비즈니스 결과가 아니라 HTTP 수준의 거절이기 때문입니다.
- **Redis 연결은 `@Lazy`:** `RateLimitConfig`의 빈은 컨텍스트 시작 시가 아니라 첫 번째 실제 HTTP 요청 시 연결됩니다. 그래서 서비스를 직접 호출하는 기존 `*ConcurrencyTest`들은 Redis 없이도 동작합니다.
- **300 VU k6 테스트가 영향받지 않는 이유:** 각 VU가 서로 다른 `userId`(`k6-user-{VU}-{ITER}`)로 한 번씩만 요청하기 때문입니다. 제한은 **같은** `userId`의 반복 요청만 막습니다.

확인 방법:

```powershell
k6 run k6/rate-limit-check.js
```

하나의 `userId`로 0.2초 간격으로 12번 요청합니다. 처음 5번은 통과하고 나머지 7번은 `429`를 반환해야 합니다.

## k6 부하 테스트

```powershell
k6 run k6/nolock.js
k6 run k6/forupdate.js
k6 run k6/optimistic.js
k6 run k6/redis.js
```

각 스크립트는 300 VU, 300회 반복으로 `FLASH50`(또는 `FLASH50-REDIS`) 캠페인에 요청을 보냅니다. `redis.js`는 `setup()`에서 캠페인을 직접 생성하고, 나머지는 먼저 위의 seed 스크립트를 실행해야 합니다.

## 결과 (k6, 동시 요청 300건, 쿠폰 50장, Windows/Docker Desktop 로컬 환경)

| 전략 | 처리량 | 평균 지연 | p95 지연 | 성공 / 품절 | 초과 발급 |
|---|---|---|---|---|---|
| 락 없음 | 80.4 req/s | 2.89s | 3.58s | 300 / 0 (전부 "SUCCESS"!) | **발생 — 6배 초과 (50장 대신 300장)** |
| SELECT FOR UPDATE | 156.4 req/s | 1.06s | 1.74s | 50 / 250 | 없음 |
| 낙관적 락 | 114.1 req/s | 2.2s | 2.53s | 50 / 250 | 없음 |
| Redis 원자적 처리 | 240.8 req/s | 390ms | 582ms | 50 / 250 | 없음 |

**락이 없는데 왜 가장 느리고 처리량이 가장 낮을까?** 언제 멈춰야 하는지 모르기 때문입니다. 300건 모두가 전체 쓰기 작업(UPDATE + INSERT)을 수행하고, 어떤 요청도 저렴한 "SOLD_OUT"으로 일찍 끝나지 않습니다. 나머지 세 전략은 50건 이후 남은 250건을 거의 비용 없이 거절하며, 이 차이가 처리량과 지연 시간을 결정합니다.

**나머지 세 전략의 공정한 비교** (모두 쓰기 50건 + 저렴한 거절 250건 수행):
- **Redis가 가장 빠름:** Lua 스크립트가 전부 Redis 메모리에서 실행되고, hot path에서 Postgres에 전혀 접근하지 않습니다(쓰기는 `@Async`로 백그라운드 처리).
- **SELECT FOR UPDATE가 두 번째:** 락 대기는 있지만 깔끔한 단일 대기열이며 재시도가 없습니다.
- **낙관적 락이 셋 중 가장 느림:** 300개 스레드가 같은 행에 동시에 몰리면 충돌이 많이 발생하고, 실패한 시도마다 처음부터 DB를 다시 조회해야 합니다(retry storm). 낙관적 락이 경합이 낮을 때는 빠르지만, 경합이 높을 때는 비관적 락보다도 느릴 수 있다는 전형적인 사례입니다.

## 프론트엔드 — 실시간 콘솔

`frontend/index.html`은 빌드도 의존성도 필요 없는 단일 HTML 파일입니다. 두 개의 컬럼으로 구성됩니다. 왼쪽은 쿠폰 발급 패널(전략 선택, 캠페인 코드, user ID, 발급 버튼), 오른쪽은 실시간 보드(서버에서 1.2초마다 읽어오는 남은 수량, 이번 세션의 결과별 카운터, 최근 60건의 실시간 로그)입니다.

운영 환경에서는 페이지와 API가 같은 도메인에서 제공되므로 "서버 주소" 필드가 자동으로 현재 주소로 설정됩니다. 로컬에서는 파일을 브라우저에서 직접 열면 기본값 `http://localhost:8080`을 사용합니다.

이를 위해 백엔드에 두 가지를 추가했습니다.
- **`GET /api/v1/{strategy}/campaigns/{code}`**: 전략별(총 4개)로 남은/전체 수량을 반환합니다. Redis 전략은 Postgres가 아닌 Redis의 실시간 `stock` 키에서 직접 읽습니다.
- **CORS** (`com.flashdrop.web.CorsConfig`): 로컬에서 파일(`file://`)로 열 때를 위해 `/api/**`에 열려 있습니다.

Redis 전략을 선택하면 "Redis 동기화(init)" 버튼이 추가로 나타나, 페이지에서 바로 캠페인을 생성하거나 초기화할 수 있습니다.

## 운영 배포

```bash
cp .env.prod.example .env
docker compose -f docker-compose.prod.yml up -d --build
```

- `app`: 멀티 스테이지 `Dockerfile`로 빌드된 Spring Boot 애플리케이션. root가 아닌 사용자로 실행되며 메모리는 1GB로 제한됩니다.
- `web`: Caddy가 `frontend/`를 정적 파일로 제공하고 `/api/*`를 애플리케이션으로 프록시합니다.
- `seed`: 시작 시 데모 캠페인 4개를 생성합니다. `docker compose -f docker-compose.prod.yml run --rm seed`로 언제든 데모를 초기화할 수 있습니다.
- Postgres와 Redis 포트는 외부에 노출하지 않으며, `POSTGRES_PASSWORD`는 `openssl rand -hex 24`로 생성합니다.

## 다음 단계

- `campaign_redis.remaining_quantity`는 현재 초기화 시에만 기록되고 이후 Redis와 동기화되지 않습니다. Postgres 상태를 최종적으로 맞추는 reconciliation 작업이 필요합니다.
- 프론트엔드 카운터는 브라우저 세션별로 동작합니다(새로고침 시 초기화). 여러 사람이 동시에 보려면 백엔드에 공용 지표 엔드포인트가 필요합니다.
