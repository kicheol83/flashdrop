package com.flashdrop.strategy.redis;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

public record CampaignInitRequest(
        @NotBlank String code,
        @NotNull @Positive Integer totalQuantity,
        @NotNull LocalDateTime startsAt
) {
}
