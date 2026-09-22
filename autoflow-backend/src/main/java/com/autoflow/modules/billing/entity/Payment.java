package com.autoflow.modules.billing.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "RAZORPAY";

    @Column(name = "provider_payment_id", nullable = false, unique = true)
    private String providerPaymentId;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "amount_inr", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountInr;

    @Column(name = "discount_inr", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal discountInr = BigDecimal.ZERO;

    @Column(name = "tax_inr", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal taxInr = BigDecimal.ZERO;

    @Column(name = "net_inr", precision = 12, scale = 2, nullable = false)
    private BigDecimal netInr;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "is_refunded", nullable = false)
    @Builder.Default
    private boolean refunded = false;

    @Column(name = "refunded_amount_inr", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal refundedAmountInr = BigDecimal.ZERO;

    @Column(name = "promo_code", length = 100)
    private String promoCode;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
