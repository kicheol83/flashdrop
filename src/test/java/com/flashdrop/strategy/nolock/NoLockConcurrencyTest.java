package com.flashdrop.strategy.nolock;

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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class NoLockConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NoLockCampaignRepository campaignRepository;

    @Autowired
    private NoLockClaimRepository claimRepository;

    @Autowired
    private NoLockClaimService claimService;

    private static final String CAMPAIGN_CODE = "FLASH50";
    private static final int TOTAL_QUANTITY = 50;
    private static final int ATTACKER_COUNT = 300;

    private Long campaignId;

    @BeforeEach
    void setUp() {
        NoLockCampaign campaign = campaignRepository.save(
                new NoLockCampaign(CAMPAIGN_CODE, TOTAL_QUANTITY, LocalDateTime.now().minusMinutes(1))
        );
        campaignId = campaign.getId();
    }

    @AfterEach
    void tearDown() {
        claimRepository.deleteAll();
        campaignRepository.deleteAll();
    }

    @Test
    void onlyTotalQuantitySuccessfulClaimsAllowedUnderConcurrentLoad() throws InterruptedException {
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

        List<NoLockClaim> claims = claimRepository.findAll();
        NoLockCampaign updated = campaignRepository.findById(campaignId).orElseThrow();

        assertThat(successCount.get())
                .as("Muvaffaqiyatli claim'lar soni jami miqdordan (%d) oshmasligi kerak", TOTAL_QUANTITY)
                .isLessThanOrEqualTo(TOTAL_QUANTITY);

        assertThat(claims)
                .as("DB'dagi claim yozuvlari soni successCount bilan mos kelishi kerak")
                .hasSize(successCount.get());

        assertThat(updated.getRemainingQuantity())
                .as("Qolgan miqdor (remaining_quantity) manfiy bo'lmasligi kerak")
                .isGreaterThanOrEqualTo(0);
    }
}
