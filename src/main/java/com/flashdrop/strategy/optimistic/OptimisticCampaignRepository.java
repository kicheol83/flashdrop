package com.flashdrop.strategy.optimistic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OptimisticCampaignRepository extends JpaRepository<OptimisticCampaign, Long> {

    Optional<OptimisticCampaign> findByCode(String code);
}
