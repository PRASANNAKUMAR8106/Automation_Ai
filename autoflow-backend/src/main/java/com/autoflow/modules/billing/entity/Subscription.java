package com.autoflow.modules.billing.entity;

import com.autoflow.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_sub_org", columnNames = {"organization_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "provider", nullable = false, length = 50)
    @Builder.Default
    private String provider = "RAZORPAY";

    @Column(name = "provider_subscription_id")
    private String providerSubscriptionId;

    @Column(name = "provider_customer_id")
    private String providerCustomerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @Column(name = "current_period_start", nullable = false)
    private Instant currentPeriodStart;

    @Column(name = "current_period_end", nullable = false)
    private Instant currentPeriodEnd;

    @Column(name = "grace_period_end")
    private Instant gracePeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private boolean cancelAtPeriodEnd = false;

    public boolean isOperable() {
        if (status == SubscriptionStatus.ACTIVE || status == SubscriptionStatus.TRIALING) {
            return true;
        }
        if (status == SubscriptionStatus.PAST_DUE && gracePeriodEnd != null) {
            return Instant.now().isBefore(gracePeriodEnd);
        }
        return false;
    }
}
