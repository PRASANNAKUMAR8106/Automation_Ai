package com.autoflow.modules.influencer.entity;

import com.autoflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id", nullable = false)
    private Influencer influencer;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    @Builder.Default
    private DiscountType discountType = DiscountType.PERCENTAGE;

    @Column(name = "discount_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal discountValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_type", nullable = false)
    @Builder.Default
    private CommissionType commissionType = CommissionType.PERCENTAGE;

    @Column(name = "commission_value", precision = 10, scale = 2, nullable = false)
    private BigDecimal commissionValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_basis", nullable = false)
    @Builder.Default
    private CommissionBasis commissionBasis = CommissionBasis.NET_QUALIFYING;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "current_uses", nullable = false)
    @Builder.Default
    private int currentUses = 0;

    @Column(name = "minimum_purchase_inr", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal minimumPurchaseInr = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private Instant startDate;

    @Column(name = "expiry_date")
    private Instant expiryDate;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE";

    public boolean isValidForCheckout(BigDecimal orderAmount) {
        if (!"ACTIVE".equalsIgnoreCase(status)) return false;
        Instant now = Instant.now();
        if (startDate != null && now.isBefore(startDate)) return false;
        if (expiryDate != null && now.isAfter(expiryDate)) return false;
        if (maxUses != null && currentUses >= maxUses) return false;
        if (minimumPurchaseInr != null && orderAmount.compareTo(minimumPurchaseInr) < 0) return false;
        return true;
    }
}
