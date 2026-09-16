package com.flashdrop.strategy.forupdate;

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
public class ForUpdateController {

    private final ForUpdateClaimService claimService;
    private final ForUpdateCampaignRepository campaignRepository;

    public ForUpdateController(ForUpdateClaimService claimService, ForUpdateCampaignRepository campaignRepository) {
        this.claimService = claimService;
        this.campaignRepository = campaignRepository;
    }

    @PostMapping("/api/v1/forupdate/campaigns/{campaignCode}/claim")
    public ResponseEntity<ClaimResult> claim(
            @PathVariable String campaignCode,
            @RequestParam @NotBlank String userId
    ) {
        return ResponseEntity.ok(claimService.claim(campaignCode, userId));
    }

    @GetMapping("/api/v1/forupdate/campaigns/{campaignCode}")
    public ResponseEntity<CampaignStatus> status(@PathVariable String campaignCode) {
        ForUpdateCampaign campaign = campaignRepository.findByCode(campaignCode)
                .orElseThrow(() -> new IllegalArgumentException("Campaign topilmadi: " + campaignCode));
        return ResponseEntity.ok(new CampaignStatus(
                campaign.getCode(), campaign.getTotalQuantity(), campaign.getRemainingQuantity()
        ));
    }
}
