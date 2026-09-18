package com.flashdrop.strategy.redis;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RedisClaimWriteBackService {

    private final RedisClaimRepository claimRepository;

    public RedisClaimWriteBackService(RedisClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    @Async("flashDropTaskExecutor")
    public void persistClaimAsync(Long campaignId, UUID claimId, String userId) {
        claimRepository.save(new RedisClaim(claimId, campaignId, userId, LocalDateTime.now()));
    }
}
