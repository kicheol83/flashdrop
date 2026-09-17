package com.flashdrop.strategy.optimistic;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
class OptimisticClaimAttempt {

    private final OptimisticCampaignRepository campaignRepository;
    private final OptimisticClaimRepository claimRepository;

    OptimisticClaimAttempt(OptimisticCampaignRepository campaignRepository, OptimisticClaimRepository claimRepository) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
    }

    @Transactional
    ClaimResult attempt(String campaignCode, String userId) {
        OptimisticCampaign campaign = campaignRepository.findByCode(campaignCode)
                .orElseThrow(() -> new IllegalArgumentException("Campaign topilmadi: " + campaignCode));

        if (campaign.getStartsAt().isAfter(LocalDateTime.now())) {
            return new ClaimResult(ClaimStatus.CAMPAIGN_NOT_STARTED, null);
        }

        if (claimRepository.existsByCampaignIdAndUserId(campaign.getId(), userId)) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, null);
        }

        if (campaign.getRemainingQuantity() <= 0) {
            return new ClaimResult(ClaimStatus.SOLD_OUT, null);
        }

        campaign.setRemainingQuantity(campaign.getRemainingQuantity() - 1);
        campaignRepository.saveAndFlush(campaign);

        OptimisticClaim claim = claimRepository.save(new OptimisticClaim(campaign.getId(), userId, LocalDateTime.now()));

        return new ClaimResult(ClaimStatus.SUCCESS, String.valueOf(claim.getId()));
    }
}
