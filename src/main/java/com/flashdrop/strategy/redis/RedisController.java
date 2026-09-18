package com.flashdrop.strategy.redis;

import com.flashdrop.common.CampaignStatus;
import com.flashdrop.common.ClaimResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/redis")
public class RedisController {

    private final RedisClaimService claimService;
    private final RedisCampaignAdminService adminService;

    public RedisController(RedisClaimService claimService, RedisCampaignAdminService adminService) {
        this.claimService = claimService;
        this.adminService = adminService;
    }

    @PostMapping("/campaigns")
    public ResponseEntity<Void> initCampaign(@RequestBody @Valid CampaignInitRequest request) {
        adminService.createAndSync(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/campaigns/{campaignCode}/claim")
    public ResponseEntity<ClaimResult> claim(
            @PathVariable String campaignCode,
            @RequestParam @NotBlank String userId
    ) {
        return ResponseEntity.ok(claimService.claim(campaignCode, userId));
    }

    @GetMapping("/campaigns/{campaignCode}")
    public ResponseEntity<CampaignStatus> status(@PathVariable String campaignCode) {
        return ResponseEntity.ok(adminService.getStatus(campaignCode));
    }
}
