package com.flashdrop.strategy.forupdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "campaign_for_update")
public class ForUpdateCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    @Column(name = "remaining_quantity", nullable = false)
    private Integer remainingQuantity;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    protected ForUpdateCampaign() {
    }

    public ForUpdateCampaign(String code, Integer totalQuantity, LocalDateTime startsAt) {
        this.code = code;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.startsAt = startsAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Integer getTotalQuantity() {
        return totalQuantity;
    }

    public Integer getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(Integer remainingQuantity) {
        this.remainingQuantity = remainingQuantity;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }
}
