package com.flashdrop.strategy.optimistic;

import com.flashdrop.common.ClaimResult;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class OptimisticClaimService {

    private static final int MAX_RETRIES = 20;

    private final OptimisticClaimAttempt claimAttempt;

    public OptimisticClaimService(OptimisticClaimAttempt claimAttempt) {
        this.claimAttempt = claimAttempt;
    }

    public ClaimResult claim(String campaignCode, String userId) {
        ObjectOptimisticLockingFailureException lastFailure = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return claimAttempt.attempt(campaignCode, userId);
            } catch (ObjectOptimisticLockingFailureException e) {
                lastFailure = e;
            }
        }

        throw new IllegalStateException(
                "Ko'p urinishdan (" + MAX_RETRIES + ") so'ng ham claim amalga oshmadi: " + campaignCode,
                lastFailure
        );
    }
}
