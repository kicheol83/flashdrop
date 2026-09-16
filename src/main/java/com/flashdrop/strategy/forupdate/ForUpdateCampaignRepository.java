package com.flashdrop.strategy.forupdate;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ForUpdateCampaignRepository extends JpaRepository<ForUpdateCampaign, Long> {

    Optional<ForUpdateCampaign> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ForUpdateCampaign c where c.code = :code")
    Optional<ForUpdateCampaign> findByCodeForUpdate(String code);
}
