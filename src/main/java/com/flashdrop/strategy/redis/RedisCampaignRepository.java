package com.flashdrop.strategy.redis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RedisCampaignRepository extends JpaRepository<RedisCampaign, Long> {

    Optional<RedisCampaign> findByCode(String code);
}
