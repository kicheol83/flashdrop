package com.flashdrop.strategy.optimistic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "claim_optimistic",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "user_id"})
)
public class OptimisticClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    protected OptimisticClaim() {
    }

    public OptimisticClaim(Long campaignId, String userId, LocalDateTime claimedAt) {
        this.campaignId = campaignId;
        this.userId = userId;
        this.claimedAt = claimedAt;
    }

    public Long getId() {
        return id;
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
