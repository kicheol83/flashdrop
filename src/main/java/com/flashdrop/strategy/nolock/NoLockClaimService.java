package com.flashdrop.strategy.nolock;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class NoLockClaimService {

    private final NoLockCampaignRepository campaignRepository;
    private final NoLockClaimRepository claimRepository;

    public NoLockClaimService(NoLockCampaignRepository campaignRepository, NoLockClaimRepository claimRepository) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
    }

    public ClaimResult claim(String campaignCode, String userId) {
        NoLockCampaign campaign = campaignRepository.findByCode(campaignCode)
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
        campaignRepository.save(campaign);

        NoLockClaim claim = claimRepository.save(new NoLockClaim(campaign.getId(), userId, LocalDateTime.now()));

        return new ClaimResult(ClaimStatus.SUCCESS, String.valueOf(claim.getId()));
    }
}
