package com.autoflow.modules.influencer.entity;

import com.autoflow.modules.billing.entity.Payment;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "commissions_ledger")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id", nullable = false)
    private Influencer influencer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribution_id", nullable = false)
    private ReferralAttribution attribution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "amount_inr", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountInr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CommissionStatus status = CommissionStatus.PENDING;

    @Column(name = "qualifies_at", nullable = false)
    private Instant qualifiesAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "reversed_at")
    private Instant reversedAt;

    @Column(name = "reversal_reason", columnDefinition = "text")
    private String reversalReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
