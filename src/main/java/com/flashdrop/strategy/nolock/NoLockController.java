package com.flashdrop.strategy.nolock;

import com.flashdrop.common.CampaignStatus;
import com.flashdrop.common.ClaimResult;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoLockController {

    private final NoLockClaimService claimService;
    private final NoLockCampaignRepository campaignRepository;

    public NoLockController(NoLockClaimService claimService, NoLockCampaignRepository campaignRepository) {
        this.claimService = claimService;
        this.campaignRepository = campaignRepository;
    }

    @PostMapping("/api/v1/nolock/campaigns/{campaignCode}/claim")
    public ResponseEntity<ClaimResult> claim(
            @PathVariable String campaignCode,
            @RequestParam @NotBlank String userId
    ) {
        return ResponseEntity.ok(claimService.claim(campaignCode, userId));
    }

    @GetMapping("/api/v1/nolock/campaigns/{campaignCode}")
    public ResponseEntity<CampaignStatus> status(@PathVariable String campaignCode) {
        NoLockCampaign campaign = campaignRepository.findByCode(campaignCode)
                .orElseThrow(() -> new IllegalArgumentException("Campaign topilmadi: " + campaignCode));
        return ResponseEntity.ok(new CampaignStatus(
                campaign.getCode(), campaign.getTotalQuantity(), campaign.getRemainingQuantity()
        ));
    }
}
