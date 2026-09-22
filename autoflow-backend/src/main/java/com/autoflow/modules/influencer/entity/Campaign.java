package com.autoflow.modules.influencer.entity;

import com.autoflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campaign extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(name = "discount_value", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false)
    @Builder.Default
    private CommissionType commissionType = CommissionType.PERCENTAGE;

    @Column(name = "commission_value", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal commissionValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_basis", nullable = false)
    @Builder.Default
    private CommissionBasis commissionBasis = CommissionBasis.NET_QUALIFYING;

    @Column(name = "attribution_window_days", nullable = false)
    @Builder.Default
    private int attributionWindowDays = 30;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";
}
