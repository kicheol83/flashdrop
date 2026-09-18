package com.flashdrop.strategy.redis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "claim_redis",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "user_id"})
)
public class RedisClaim implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @Transient
    private boolean isNew = true;

    protected RedisClaim() {
    }

    public RedisClaim(UUID id, Long campaignId, String userId, LocalDateTime claimedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.userId = userId;
        this.claimedAt = claimedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    public Long getCampaignId() {
        return campaignId;
    }

    public String getUserId() {
        return userId;
    }

    public LocalDateTime getClaimedAt() {
        return claimedAt;
    }
}

