package com.flashdrop.strategy.optimistic;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OptimisticClaimRepository extends JpaRepository<OptimisticClaim, Long> {

    boolean existsByCampaignIdAndUserId(Long campaignId, String userId);
}
