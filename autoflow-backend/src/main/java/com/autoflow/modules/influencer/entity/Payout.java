package com.autoflow.modules.influencer.entity;

import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payouts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "influencer_id", nullable = false)
    private Influencer influencer;

    @Column(name = "amount_inr", precision = 12, scale = 2, nullable = false)
    private BigDecimal amountInr;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.REQUESTED;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "admin_notes", columnDefinition = "text")
    private String adminNotes;

    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private User processedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
