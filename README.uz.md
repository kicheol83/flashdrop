# FlashDrop — 선착순 발급 tizimi

10,000 dona kupon, minglab parallel so'rov, bittasi ham ortiqcha berilmasligi kerak. Bir xil biznes qoidasini 4 xil konkurentlik strategiyasi bilan yechib, k6 bilan solishtirish uchun loyiha.

## 4 ta yondashuv — barchasi tayyor

<<<<<<< HEAD
| #   | Strategiya                                 | Endpoint                                         | Fayllar               |
| --- | ------------------------------------------ | ------------------------------------------------ | --------------------- |
| 1   | Locksiz (ataylab buzuq — oversell bo'ladi) | `POST /api/v1/nolock/campaigns/{code}/claim`     | `strategy.nolock`     |
| 2   | `SELECT ... FOR UPDATE`                    | `POST /api/v1/forupdate/campaigns/{code}/claim`  | `strategy.forupdate`  |
| 3   | Optimistic lock (`@Version` + retry)       | `POST /api/v1/optimistic/campaigns/{code}/claim` | `strategy.optimistic` |
| 4   | Redis atomik dekrement + async yozuv       | `POST /api/v1/redis/campaigns/{code}/claim`      | `strategy.redis`      |
=======
| # | Strategiya | Endpoint | Fayllar |
|---|---|---|---|
| 1 | Locksiz (ataylab buzuq — oversell bo'ladi) | `POST /api/v1/nolock/campaigns/{code}/claim` | `strategy.nolock` |
| 2 | `SELECT ... FOR UPDATE` | `POST /api/v1/forupdate/campaigns/{code}/claim` | `strategy.forupdate` |
| 3 | Optimistic lock (`@Version` + retry) | `POST /api/v1/optimistic/campaigns/{code}/claim` | `strategy.optimistic` |
| 4 | Redis atomik dekrement + async yozuv | `POST /api/v1/redis/campaigns/{code}/claim` | `strategy.redis` |
>>>>>>> 96a097ac82d44c6cc4a300e9a67cf088322765c0

Har biri o'z jadvali, o'z entity'lari va o'z endpoint'i bilan izolyatsiyalangan — shunda k6 natijalari bir-biriga aralashmaydi.

## Har bir strategiya qanday ishlaydi

<<<<<<< HEAD
**1 — Locksiz.** `findByCode` → `remainingQuantity` o'qiladi → tekshiriladi → 1 ga kamaytirilib saqlanadi. `@Transactional` yo'q, lock yo'q. Ikki request bir xil qiymatni o'qib, ikkalasi ham "bor" deb qaror qiladi — klassik _lost update_. Test buni `successCount > 50` yoki `remaining_quantity < 0` orqali ko'rsatadi.
=======
**1 — Locksiz.** `findByCode` → `remainingQuantity` o'qiladi → tekshiriladi → 1 ga kamaytirilib saqlanadi. `@Transactional` yo'q, lock yo'q. Ikki request bir xil qiymatni o'qib, ikkalasi ham "bor" deb qaror qiladi — klassik *lost update*. Test buni `successCount > 50` yoki `remaining_quantity < 0` orqali ko'rsatadi.
>>>>>>> 96a097ac82d44c6cc4a300e9a67cf088322765c0

**2 — SELECT FOR UPDATE.** `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` bilan qatorni o'qiganda darhol lock qo'yiladi; boshqa tranzaksiyalar shu qatorga navbatga turadi. To'g'ri, lekin yuk ostida lock kutish (contention) throughput'ni pasaytiradi — buni k6 raqamlarida ko'rasiz.

**3 — Optimistic lock.** Hech narsa lock qilinmaydi, faqat `version` ustuni bilan "men o'qiganimdan beri o'zgarmadimi" tekshiriladi. To'qnashuv bo'lsa `ObjectOptimisticLockingFailureException` otiladi va qayta uriniladi (`OptimisticClaimService`, 20 martagacha retry). Muhim nuqta: retry-transactional logikasi ataylab ikkita bean'ga bo'lingan (`OptimisticClaimService` → `OptimisticClaimAttempt`), chunki Spring'ning `@Transactional` proxy'si **self-invocation**'ni (bitta bean ichida bir metod ikkinchisini chaqirishi) ushlamaydi — shu sababli tranzaksiya haqiqatda ishlamay qoladi, agar hammasi bitta klassda bo'lsa.

**4 — Redis.** Stock Redis'da (`DECR`), idempotency ham Redis'da (`SADD` bilan claimed-users set) — ikkalasi bitta Lua skript (`claim.lua`) ichida atomik bajariladi, shu bois check-then-act muammosi umuman yo'q. Postgres'ga yozish esa `@Async` orqali fon rejimida (`RedisClaimWriteBackService`) — response darhol qaytadi, DB yozuvi orqadan yetib keladi. Buning narxi: agar ilova Redis'dan keyin, DB'ga yozishdan oldin qulasa, Redis'da "berilgan" deb hisoblangan kupon Postgres'da yo'q bo'lib qolishi mumkin — bu trade-off'ni solishtirish jadvalida muhokama qiling.

Har bir strategiyada `CAMPAIGN_NOT_STARTED` / `ALREADY_CLAIMED` / `SOLD_OUT` / `SUCCESS` bir xil `ClaimResult` (`common` paketi) orqali qaytadi. Idempotency barcha strategiyalarda ta'minlangan: 1–3 da `UNIQUE(campaign_id, user_id)` DB darajasida, 4-da Redis SET + xavfsizlik uchun DB'da ham unique constraint.

## Stack

Java 21 (virtual thread'lar testlarda ishlatilgan) · Spring Boot 3.5 · PostgreSQL 17 · Redis · Flyway · Testcontainers · k6

`application.yml`'da Tomcat (`accept-count`, `threads.max`) va HikariCP (`maximum-pool-size: 50`) sozlamalari standartdan kattaroq qilib qo'yilgan — standart (10 connection, kichik backlog) 300 ta bir vaqtdagi so'rov ostida sun'iy "connection refused" va navbat kechikishlarini keltirib chiqargani k6 bilan sinovda aniqlandi.

## Ishga tushirish (PowerShell)

```powershell
docker compose up -d
```

Gradle wrapper qo'shilmagan (sandbox'da internet cheklovi tufayli generatsiya qila olmadim):

```powershell
gradle wrapper --gradle-version 8.10
.\gradlew.bat bootRun
```

yoki IntelliJ IDEA'da papkani oching — Gradle avtomatik sinxronlashadi.

## Demo kampaniyalarni yaratish

1–3-strategiyalar uchun (Redis o'zining `/api/v1/redis/campaigns` endpoint'i orqali o'zini sinxronlaydi, alohida seed shart emas):

```powershell
Get-Content scripts/seed-demo-campaigns.sql | docker exec -i flashdrop-postgres-1 psql -U flashdrop -d flashdrop
```

(Konteyner nomi `docker ps` bilan tekshiring — `docker compose up -d` odatda `flashdrop-postgres-1` deb nomlaydi.)

Redis strategiyasi uchun serverni ishga tushirgach:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/redis/campaigns `
  -ContentType "application/json" `
  -Body '{"code":"FLASH50-REDIS","totalQuantity":50,"startsAt":"2026-01-01T00:00:00"}'
```

## Testlarni ishga tushirish

```powershell
.\gradlew.bat test
```

Har bir strategiya o'z Testcontainers-asosidagi konkurentlik testiga ega (`*ConcurrencyTest`), 300 ta virtual thread bir vaqtning o'zida 50 dona kuponga hujum qiladi:

- `NoLockConcurrencyTest` — **qizil kutiladi**: oversell'ni isbotlaydi (`successCount` 50'dan oshadi yoki `remaining_quantity` manfiyga tushadi)
- `ForUpdateConcurrencyTest`, `OptimisticConcurrencyTest`, `RedisConcurrencyTest` — **yashil kutiladi**: aniq 50 ta muvaffaqiyatli claim, ortiqcha yo'q

Bu sandbox muhitida (internet cheklovi + Docker yo'qligi tufayli) testlarni haqiqatda ishga tushirib tekshira olmadim — kodni diqqat bilan qo'lda tekshirdim, lekin birinchi marta ishga tushirganingizda natijani menga ayting, kerak bo'lsa birga tuzatamiz.

## Rate limiting

Barcha 4 strategiyaning `/claim` endpoint'i bitta umumiy interceptor orqali himoyalangan (`com.flashdrop.ratelimit`), alohida-alohida yozilmagan — `/api/v1/*/campaigns/*/claim` pattern'iga mos keladigan har qanday so'rov shu orqali o'tadi.

- **Algoritm:** token bucket (Bucket4j), holat Redis'da saqlanadi (Lettuce orqali) — shuning uchun ilova bir nechta nusxada ishlasa ham limit umumiy bo'ladi.
- **Kalit:** `userId` (loyihada allaqachon shu identifikator ishlatiladi). Bu himoya kuchsiz — userId o'zi e'lon qilingani uchun istalgan qiymatga o'zgartirilishi mumkin; productionda IP bilan birga ishlatiladi.
- **Standart limit:** 10 soniyada 5 ta so'rov, bitta userId uchun (`application.yml` → `flashdrop.rate-limit.*`).
- **Javob:** limitdan oshganda 200 emas, **429 Too Many Requests**, tanasi `{"status":"RATE_LIMITED","claimId":null}`, sarlavhada `Retry-After` va `X-Rate-Limit-Remaining`. Bu — boshqa barcha holatlar (SUCCESS/SOLD_OUT/...) 200 qaytarishidan ataylab qilingan farq: bu biznes natijasi emas, haqiqiy HTTP darajasidagi rad javobi.
- **Redis ulanishi `@Lazy`** — `RateLimitConfig`dagi bean context ishga tushganda emas, birinchi HAQIQIY HTTP so'rov kelganda ulanadi. Shuning uchun mavjud `*ConcurrencyTest`lar (ular service'larni to'g'ridan-to'g'ri chaqiradi, HTTP orqali emas) buzilmaydi — Redis kerak bo'lmaydi, chunki interceptor umuman ishga tushmaydi.
- **300 VU'lik k6 testlaringiz nega buzilmaydi:** har biri boshqa-boshqa `userId` ishlatadi (`k6-user-{VU}-{ITER}`), har biri atigi bir marta so'raydi — limit esa faqat **bitta** userId qayta-qayta urinishini ushlaydi.

Ishlashini ko'rish uchun:

```powershell
k6 run k6/rate-limit-check.js
```

<<<<<<< HEAD
Bitta userId bilan 0.2s oraliq bilan 12 marta (taxminan 2.4s) so'raydi. Bucket 5 ta token bilan boshlanadi va greedy refill tufayli har 2 soniyada 1 tadan qayta to'ladi, shuning uchun test davomida yana bitta token qo'shiladi. O'lchangan natija: **6 tasi o'tadi, 6 tasi `429`**. Har bir urinish natijasi konsolga (`console.log`) chiqadi.
=======
Bitta userId bilan 12 marta ketma-ket so'raydi (0.2s oraliq bilan) — birinchi 5 tasi o'tadi, qolgan 7 tasi `429` bilan qaytishi kerak. Har bir urinish natijasi konsolga (`console.log`) chiqadi.
>>>>>>> 96a097ac82d44c6cc4a300e9a67cf088322765c0

## k6 bilan yuklama testi

```powershell
k6 run k6/nolock.js
k6 run k6/forupdate.js
k6 run k6/optimistic.js
k6 run k6/redis.js
```

Har biri 300 VU, 300 iteratsiya bilan `FLASH50` (yoki `FLASH50-REDIS`) kampaniyasiga hujum qiladi. `redis.js` o'zi `setup()` orqali kampaniyani yaratadi va sinxronlaydi — boshqalari uchun avval yuqoridagi seed skriptini ishga tushiring.

<<<<<<< HEAD
Butun taqqoslashni bir martada takrorlash uchun (server ishlab turgan, brauzer konsoli yopiq holatda):

```powershell
powershell -ExecutionPolicy Bypass -File scripts\bench.ps1
```

Har bir strategiya uchun seed → 1 ta qizdirish (warm-up) → 3 ta o'lchov run'ini bajaradi va natijalarni `docs/evidence/bench/` ga saqlaydi (`summary.csv`, `results.csv`, har run'ning JSON'i, `environment.txt`).

## Natijalar (k6, 300 parallel so'rov, 50 dona kupon, Windows/Docker Desktop lokal muhit, 2026-10-09)

O'lchash usuli: `scripts/bench.ps1` bilan har strategiya uchun 1 ta qizdirish run'idan (hisobga olinmaydi) keyingi 3 ta run'ning **median**'i. Bitta bo'lsa ham muvaffaqiyatsiz so'rovi bor run yaroqsiz deb belgilanib, qayta ishga tushirildi (pastdagi "O'lchash paytida topilgan muammo"ga qarang).

| Strategiya        | Throughput | avg latency | p95 latency | Muvaffaqiyatli / Sold out    | Haqiqatda chiqarilgan (DB) |
| ----------------- | ---------- | ----------- | ----------- | ---------------------------- | -------------------------- |
| Locksiz           | 307 req/s  | 531ms       | 867ms       | 300 / 0 (hammasi "SUCCESS"!) | **300 — 6x oversell**      |
| SELECT FOR UPDATE | 188 req/s  | 880ms       | 1465ms      | 50 / 250                     | 50                         |
| Optimistic lock   | 331 req/s  | 677ms       | 831ms       | 50 / 250                     | 50                         |
| Redis atomik      | 873 req/s  | 123ms       | 178ms       | 50 / 250                     | 50                         |

**Locksiz usul tez, lekin noto'g'ri.** Lock kutish yo'qligi uchun throughput'i SELECT FOR UPDATE'dan yuqori, ammo 50 o'rniga 300 ta kupon chiqardi. Shunga qaramay `remaining_quantity` 0 emas, 46 ko'rsatdi (o'lchangan). Hamma so'rov bir xil qiymatni o'qib, bir-birining yozuvini ustidan bosgan: klassik _lost update_. Noto'g'ri ishlaydigan implementatsiyaning tezligini solishtirish ma'noga ega emas.

**To'g'ri ishlaydigan uchta strategiyani solishtirish** (har biri 50 ta yozish + 250 ta rad bajardi):

- **Redis eng tez** (SELECT FOR UPDATE'ga nisbatan throughput 4.6x, p95 8.2x yaxshi): Lua skript Redis xotirasida atomik ishlaydi, hot path'da Postgres'ga umuman murojaat qilinmaydi (yozish `@Async` bilan fonda).
- **Optimistic lock SELECT FOR UPDATE'dan tez** (throughput 1.76x): `findByCodeForUpdate` qoldiqni tekshirishdan **oldin** row lock oladi, shuning uchun sotuv tugagandan keyin rad etiladigan 250 ta so'rov ham bitta navbatda kutadi. Optimistic lock esa faqat 50 ta yozish atrofida to'qnashadi; qoldiq 0 bo'lgach, qolgan so'rovlar lock'siz parallel o'qib, darhol `SOLD_OUT` qaytaradi.
- **SELECT FOR UPDATE eng sekin:** to'g'ri ishlaydi, lekin rad etiladiganlari bilan birga hamma so'rovni ketma-ket qiladi. Yaxshilash yo'li: lock olishdan oldin oddiy SELECT bilan sotuv tugaganini tekshirish, shunda sotuvdan keyingi so'rovlar lock kutmaydi.

**Oldingi o'lchovdan farqi:** README'ning oldingi versiyasida locksiz usul va optimistic lock eng sekin chiqqan edi. U jadval qizdirishsiz, bitta run natijasidan olingan, ehtimol JIT va connection pool hali tayyor bo'lmagan holatni aks ettirgan, shuning uchun yuqoridagi usul bilan qayta o'lchandi.

**O'lchash paytida topilgan muammo:** 20 ta urinishdan 4 tasida 84–85 ta so'rov TCP darajasida rad etildi (`connection refused`). Rad etilganlar soni har safar deyarli bir xil bo'lgani uchun bu tasodifiy tarmoq xatosi emas, balki OS'ning listen backlog'i to'lib qolgani (OS cheklovi Tomcat'dagi `accept-count: 500` dan past). Bunday run'larda muvaffaqiyatli + sold out yig'indisi 300 dan kam bo'lib, natija haqiqatdan tezroq ko'rinadi, shuning uchun `bench.ps1` ularni avtomatik yaroqsiz deb belgilab, qayta ishga tushiradi. Yaroqsiz run'lar ham `results.csv` da qayd sifatida saqlanadi.
=======
## Natijalar (k6, 300 parallel so'rov, 50 dona kupon, Windows/Docker Desktop lokal muhit)

| Strategiya | Throughput | avg latency | p95 latency | Muvaffaqiyatli / Sold out | Oversell bormi? |
|---|---|---|---|---|---|
| Locksiz | 80.4 req/s | 2.89s | 3.58s | 300 / 0 (hammasi "SUCCESS"!) | **HA — 6x oversell (300 ta 50 o'rniga)** |
| SELECT FOR UPDATE | 156.4 req/s | 1.06s | 1.74s | 50 / 250 | Yo'q |
| Optimistic lock | 114.1 req/s | 2.2s | 2.53s | 50 / 250 | Yo'q |
| Redis atomik | 240.8 req/s | 390ms | 582ms | 50 / 250 | Yo'q |

**Locksiz nega eng sekin va eng past throughput'ga ega, garchi "lock yo'q" bo'lsa ham?** Chunki u qachon to'xtashni bilmaydi — 300 ta so'rovning barchasi to'liq yozish ishini (UPDATE + INSERT) bajaradi, hech biri arzon "SOLD_OUT" bilan erta chiqib ketmaydi. Qolgan uch strategiya 50 tadan keyin qolgan 250 tasini deyarli bepul rad etadi — shu farq throughput'ni ham, latency'ni ham belgilaydi.

**Qolgan uchtasini xolis solishtirish** (har biri xuddi 50 yozish + 250 arzon rad bajardi):
- **Redis eng tez** — Lua skript butunlay Redis xotirasida ishlaydi, Postgres'ga hot path'da umuman murojaat qilinmaydi (yozish `@Async` bilan fonda).
- **SELECT FOR UPDATE ikkinchi** — lock kutish bor, lekin toza, bitta navbat, retry yo'q.
- **Optimistic lock eng sekin uchdan** — 300 ta thread bir xil qatorga bir vaqtda hujum qilganda ko'p to'qnashuv yuz beradi, har bir muvaffaqiyatsiz urinish qayta boshidan bazaga borishni talab qiladi (retry storm). Bu — optimistic lock'ning past raqobatda tez, yuqori raqobatda esa pessimistic lock'dan ham sekin bo'lishi mumkinligining klassik namunasi.
>>>>>>> 96a097ac82d44c6cc4a300e9a67cf088322765c0

## Frontend — jonli konsol

`frontend/index.html` — bitta mustaqil HTML fayl (build kerak emas, hech qanday dependency yo'q). Ikki ustunli: chapda kupon olish paneli (strategiya tanlash, kampaniya kodi, user ID, "Kupon olish" tugmasi), o'ngda jonli taxta (qolgan miqdor — katta raqam, serverdan har 1.2 soniyada o'qiladi; SUCCESS/SOLD_OUT/ALREADY_CLAIMED/CAMPAIGN_NOT_STARTED/RATE_LIMITED bo'yicha shu sessiyadagi hisoblagichlar; oxirgi 60 ta urinishning jonli logi).

Ishga tushirish:
<<<<<<< HEAD

```powershell
.\gradlew.bat bootRun
```

keyin `frontend\index.html` faylini brauzerda oching (fayl tizimidan to'g'ridan-to'g'ri, alohida server shart emas). "Server manzili" maydoni standart `http://localhost:8080` — boshqa portda ishlatsangiz shu yerda o'zgartiring.

Buning uchun backend'ga ikkita narsa qo'shildi:

=======
```powershell
.\gradlew.bat bootRun
```
keyin `frontend\index.html` faylini brauzerda oching (fayl tizimidan to'g'ridan-to'g'ri, alohida server shart emas). "Server manzili" maydoni standart `http://localhost:8080` — boshqa portda ishlatsangiz shu yerda o'zgartiring.

Buning uchun backend'ga ikkita narsa qo'shildi:
>>>>>>> 96a097ac82d44c6cc4a300e9a67cf088322765c0
- **`GET /api/v1/{strategy}/campaigns/{code}`** — har bir strategiya uchun (jami 4 ta), qolgan/jami miqdorni qaytaradi. Redis strategiyasida bu Postgres'dan emas, to'g'ridan-to'g'ri Redis'dagi jonli `stock` kalitidan o'qiydi — chunki productionda ham "haqiqat manbai" shu.
- **CORS** (`com.flashdrop.web.CorsConfig`) — `/api/**` uchun ochiq, chunki frontend fayl sifatida (`file://`) yoki boshqa portdan ochiladi, brauzer standart holatda buni bloklaydi. Faqat lokal demo uchun — productionda aniq origin'lar ro'yxati kerak bo'lardi.

Redis strategiyasi tanlanganda qo'shimcha "Redis'ni sinxronlash (init)" tugmasi chiqadi — PowerShell'ga chiqmasdan, to'g'ridan-to'g'ri sahifadan campaign yaratish/qayta tiklash mumkin. (Sync bosishdan oldin sahifa mavjud bo'lmagan kampaniyani so'rab turadi — bu normal, `com.flashdrop.web.GlobalExceptionHandler` buni server konsolida shovqin qilmaydigan toza 404'ga aylantiradi.)

## Keyingi qadamlar

- `campaign_redis.remaining_quantity` hozircha faqat init paytida yoziladi, keyin Redis bilan sinxronlanmaydi — Postgres'dagi holatni yakunda moslashtirish (reconciliation) uchun alohida job kerak bo'ladi.
- Frontend'dagi hisoblagichlar shu brauzer sessiyasiga xos (sahifa yangilansa nolga tushadi) — ko'p odam bir vaqtda kuzatishi kerak bo'lsa, umumiy metrikalar uchun backend'da alohida endpoint kerak bo'lardi.
