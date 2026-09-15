package com.flashdrop.strategy.nolock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoLockCampaignRepository extends JpaRepository<NoLockCampaign, Long> {

    Optional<NoLockCampaign> findByCode(String code);
}
