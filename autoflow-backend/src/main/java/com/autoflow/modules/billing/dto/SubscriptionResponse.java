package com.autoflow.modules.billing.dto;

import com.autoflow.modules.billing.entity.Subscription;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {

    private UUID id;
    private UUID organizationId;
    private String planCode;
    private String planName;
    private String provider;
    private String providerSubscriptionId;
    private String status;
    private Instant currentPeriodStart;
    private Instant currentPeriodEnd;
    private Instant gracePeriodEnd;
    private boolean cancelAtPeriodEnd;
    private boolean operable;

    public static SubscriptionResponse fromEntity(Subscription sub) {
        if (sub == null) return null;
        return SubscriptionResponse.builder()
                .id(sub.getId())
                .organizationId(sub.getOrganizationId())
                .planCode(sub.getPlan() != null ? sub.getPlan().getCode() : null)
                .planName(sub.getPlan() != null ? sub.getPlan().getName() : null)
                .provider(sub.getProvider())
                .providerSubscriptionId(sub.getProviderSubscriptionId())
                .status(sub.getStatus() != null ? sub.getStatus().name() : null)
                .currentPeriodStart(sub.getCurrentPeriodStart())
                .currentPeriodEnd(sub.getCurrentPeriodEnd())
                .gracePeriodEnd(sub.getGracePeriodEnd())
                .cancelAtPeriodEnd(sub.isCancelAtPeriodEnd())
                .operable(sub.isOperable())
                .build();
    }
}
