package com.flashdrop.strategy.redis;

import com.flashdrop.common.ClaimResult;
import com.flashdrop.common.ClaimStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RedisClaimService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> claimScript;
    private final RedisClaimWriteBackService writeBackService;

    public RedisClaimService(
            StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> claimScript,
            RedisClaimWriteBackService writeBackService
    ) {
        this.redisTemplate = redisTemplate;
        this.claimScript = claimScript;
        this.writeBackService = writeBackService;
    }

    public ClaimResult claim(String campaignCode, String userId) {
        String startsAtRaw = redisTemplate.opsForValue().get(RedisKeys.startsAt(campaignCode));
        if (startsAtRaw == null) {
            throw new IllegalStateException("Campaign Redis'da sinxronlanmagan: " + campaignCode);
        }

        long startsAtEpochMillis = Long.parseLong(startsAtRaw);
        if (startsAtEpochMillis > System.currentTimeMillis()) {
            return new ClaimResult(ClaimStatus.CAMPAIGN_NOT_STARTED, null);
        }

        List<String> keys = List.of(RedisKeys.stock(campaignCode), RedisKeys.claimed(campaignCode));
        Long resultCode = redisTemplate.execute(claimScript, keys, userId);

        if (resultCode == null || resultCode == -1L) {
            throw new IllegalStateException("Campaign Redis'da sinxronlanmagan: " + campaignCode);
        }
        if (resultCode == -2L) {
            return new ClaimResult(ClaimStatus.ALREADY_CLAIMED, null);
        }
        if (resultCode == 0L) {
            return new ClaimResult(ClaimStatus.SOLD_OUT, null);
        }

        String campaignIdRaw = redisTemplate.opsForValue().get(RedisKeys.campaignId(campaignCode));
        Long campaignId = Long.valueOf(campaignIdRaw);
        UUID claimId = UUID.randomUUID();

        writeBackService.persistClaimAsync(campaignId, claimId, userId);

        return new ClaimResult(ClaimStatus.SUCCESS, claimId.toString());
    }
}
