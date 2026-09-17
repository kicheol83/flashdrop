package com.flashdrop.strategy.optimistic;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class OptimisticConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OptimisticCampaignRepository campaignRepository;

    @Autowired
    private OptimisticClaimRepository claimRepository;

    @Autowired
    private OptimisticClaimService claimService;

    private static final String CAMPAIGN_CODE = "FLASH50";
    private static final int TOTAL_QUANTITY = 50;
    private static final int ATTACKER_COUNT = 300;

    private Long campaignId;

    @BeforeEach
    void setUp() {
        OptimisticCampaign campaign = campaignRepository.save(
                new OptimisticCampaign(CAMPAIGN_CODE, TOTAL_QUANTITY, LocalDateTime.now().minusMinutes(1))
        );
        campaignId = campaign.getId();
    }

    @AfterEach
    void tearDown() {
        claimRepository.deleteAll();
        campaignRepository.deleteAll();
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

        OptimisticCampaign updated = campaignRepository.findById(campaignId).orElseThrow();

        assertThat(successCount.get())
                .as("Optimistic lock bilan muvaffaqiyatli claim'lar aniq %d ta bo'lishi kerak", TOTAL_QUANTITY)
                .isEqualTo(TOTAL_QUANTITY);

        assertThat(claimRepository.count())
                .as("DB'dagi claim yozuvlari successCount bilan mos kelishi kerak")
                .isEqualTo(successCount.get());

        assertThat(updated.getRemainingQuantity())
                .as("Yakunda qolgan miqdor aniq 0 bo'lishi kerak")
                .isEqualTo(0);
    }
}
