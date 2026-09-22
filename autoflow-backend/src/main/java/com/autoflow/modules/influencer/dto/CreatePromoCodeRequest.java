package com.autoflow.modules.influencer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePromoCodeRequest {

    @NotNull(message = "Campaign ID is required")
    private UUID campaignId;

    @NotNull(message = "Influencer ID is required")
    private UUID influencerId;

    @NotBlank(message = "Promo code string is required")
    private String code;

    private Integer maxUses;

    private BigDecimal minimumPurchaseInr;
}
