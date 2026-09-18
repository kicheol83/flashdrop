package com.flashdrop.strategy.redis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RedisClaimRepository extends JpaRepository<RedisClaim, UUID> {

    void deleteByCampaignId(Long campaignId);
}
