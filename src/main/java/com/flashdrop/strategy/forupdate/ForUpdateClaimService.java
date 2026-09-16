package com.flashdrop.strategy.forupdate;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ForUpdateClaimService {

    private final ForUpdateCampaignRepository campaignRepository;
    private final ForUpdateClaimRepository claimRepository;

    public ForUpdateClaimService(ForUpdateCampaignRepository campaignRepository, ForUpdateClaimRepository claimRepository) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
    }

    @Transactional
    public ClaimResult claim(String campaignCode, String userId) {
        ForUpdateCampaign campaign = campaignRepository.findByCodeForUpdate(campaignCode)
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

        ForUpdateClaim claim = claimRepository.save(new ForUpdateClaim(campaign.getId(), userId, LocalDateTime.now()));

        return new ClaimResult(ClaimStatus.SUCCESS, String.valueOf(claim.getId()));
    }
}
