package com.flashdrop.strategy.nolock;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NoLockClaimRepository extends JpaRepository<NoLockClaim, Long> {

    boolean existsByCampaignIdAndUserId(Long campaignId, String userId);
}
