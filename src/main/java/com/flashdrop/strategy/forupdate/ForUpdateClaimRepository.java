package com.flashdrop.strategy.forupdate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ForUpdateClaimRepository extends JpaRepository<ForUpdateClaim, Long> {

    boolean existsByCampaignIdAndUserId(Long campaignId, String userId);
}
