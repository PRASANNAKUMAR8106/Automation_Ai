package com.autoflow.modules.influencer.dto;

import com.autoflow.modules.influencer.entity.CommissionBasis;
import com.autoflow.modules.influencer.entity.CommissionType;
import com.autoflow.modules.influencer.entity.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequest {

    @NotBlank(message = "Campaign name is required")
    private String name;

    private String description;

    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @NotNull
    private BigDecimal discountValue;

    @Builder.Default
    private CommissionType commissionType = CommissionType.PERCENTAGE;

    @NotNull
    private BigDecimal commissionValue;

    @Builder.Default
    private CommissionBasis commissionBasis = CommissionBasis.NET_QUALIFYING;

    @Builder.Default
    private int attributionWindowDays = 30;
}
