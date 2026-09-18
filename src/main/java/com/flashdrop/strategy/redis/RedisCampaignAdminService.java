package com.flashdrop.strategy.redis;

import com.flashdrop.common.CampaignStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

@Service
public class RedisCampaignAdminService {

    private final RedisCampaignRepository campaignRepository;
    private final RedisClaimRepository claimRepository;
    private final StringRedisTemplate redisTemplate;

    public RedisCampaignAdminService(
            RedisCampaignRepository campaignRepository,
            RedisClaimRepository claimRepository,
            StringRedisTemplate redisTemplate
    ) {
        this.campaignRepository = campaignRepository;
        this.claimRepository = claimRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void createAndSync(CampaignInitRequest request) {
        RedisCampaign campaign = campaignRepository.findByCode(request.code()).orElse(null);

        if (campaign == null) {
            campaign = campaignRepository.save(
                    new RedisCampaign(request.code(), request.totalQuantity(), request.startsAt())
            );
        } else {
            campaign.setRemainingQuantity(request.totalQuantity());
            claimRepository.deleteByCampaignId(campaign.getId());
        }

        long startsAtEpochMillis = request.startsAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForValue().set(RedisKeys.stock(request.code()), String.valueOf(request.totalQuantity()));
        redisTemplate.opsForValue().set(RedisKeys.campaignId(request.code()), String.valueOf(campaign.getId()));
        redisTemplate.opsForValue().set(RedisKeys.startsAt(request.code()), String.valueOf(startsAtEpochMillis));
        redisTemplate.delete(RedisKeys.claimed(request.code()));
    }

    public CampaignStatus getStatus(String code) {
        RedisCampaign campaign = campaignRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Campaign topilmadi: " + code));

        String stockRaw = redisTemplate.opsForValue().get(RedisKeys.stock(code));
        Integer remaining = stockRaw != null ? Integer.valueOf(stockRaw) : 0;

        return new CampaignStatus(campaign.getCode(), campaign.getTotalQuantity(), remaining);
    }
}
