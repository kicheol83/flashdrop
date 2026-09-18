package com.flashdrop.strategy.redis;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class RedisConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisCampaignRepository campaignRepository;

    @Autowired
    private RedisClaimRepository claimRepository;

    @Autowired
    private RedisClaimService claimService;

    @Autowired
    private RedisCampaignAdminService adminService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String CAMPAIGN_CODE = "FLASH50-REDIS";
    private static final int TOTAL_QUANTITY = 50;
    private static final int ATTACKER_COUNT = 300;

    @BeforeEach
    void setUp() {
        adminService.createAndSync(
                new CampaignInitRequest(CAMPAIGN_CODE, TOTAL_QUANTITY, LocalDateTime.now().minusMinutes(1))
        );
    }

    @AfterEach
    void tearDown() {
        claimRepository.deleteAll();
        campaignRepository.deleteAll();
        redisTemplate.delete(RedisKeys.stock(CAMPAIGN_CODE));
        redisTemplate.delete(RedisKeys.claimed(CAMPAIGN_CODE));
        redisTemplate.delete(RedisKeys.campaignId(CAMPAIGN_CODE));
        redisTemplate.delete(RedisKeys.startsAt(CAMPAIGN_CODE));
    }

    @Test
    void exactlyTotalQuantitySuccessfulClaimsUnderConcurrentLoad() throws InterruptedException {
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(ATTACKER_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            IntStream.range(0, ATTACKER_COUNT).forEach(i -> executor.submit(() -> {
                try {
                    startLine.await();
                    ClaimResult result = claimService.claim(CAMPAIGN_CODE, "user-" + i);
                    if (result.status() == ClaimStatus.SUCCESS) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLine.countDown();
                }
            }));

            startLine.countDown();
            finishLine.await();
        }

        assertThat(successCount.get())
                .as("Redis atomik dekrement bilan muvaffaqiyatli claim'lar aniq %d ta bo'lishi kerak", TOTAL_QUANTITY)
                .isEqualTo(TOTAL_QUANTITY);

        String remainingStock = redisTemplate.opsForValue().get(RedisKeys.stock(CAMPAIGN_CODE));
        assertThat(Integer.parseInt(remainingStock))
                .as("Redis'dagi qolgan stock manfiy bo'lmasligi kerak")
                .isGreaterThanOrEqualTo(0);

        awaitClaimCount(TOTAL_QUANTITY, Duration.ofSeconds(5));
    }

    private void awaitClaimCount(int expected, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (claimRepository.count() == expected) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(claimRepository.count())
                .as("Async yozuv %d ms ichida DB'ga tugamadi", timeout.toMillis())
                .isEqualTo((long) expected);
    }
}
