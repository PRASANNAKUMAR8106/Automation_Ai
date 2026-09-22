package com.autoflow.modules.influencer.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InfluencerStatsResponse {

    private UUID influencerId;
    private String name;
    private String email;
    private String instagramHandle;
    private String promoCode;
    private String referralLink;
    private long totalClicks;
    private BigDecimal pendingCommissionInr;
    private BigDecimal approvedCommissionInr;
    private BigDecimal paidCommissionInr;
}
