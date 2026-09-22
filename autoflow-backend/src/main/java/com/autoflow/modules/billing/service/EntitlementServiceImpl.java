package com.autoflow.modules.billing.service;

import com.autoflow.common.exceptions.AutoFlowException;
import com.autoflow.common.exceptions.QuotaExceededException;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.PlanFeature;
import com.autoflow.modules.billing.entity.Subscription;
import com.autoflow.modules.billing.entity.SubscriptionStatus;
import com.autoflow.modules.billing.repository.PlanRepository;
import com.autoflow.modules.billing.repository.SubscriptionRepository;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntitlementServiceImpl implements EntitlementService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final WorkflowRepository workflowRepository;
    private final ConnectedAccountRepository connectedAccountRepository;

    private static final Map<String, Long> DEFAULT_CHANNEL_LIMITS = Map.of(
            "FREE", 1L,
            "STARTER", 2L,
            "PRO", 5L,
            "BUSINESS", 15L,
            "AGENCY", 100L
    );

    @Override
    public void assertEntitlement(UUID organizationId, String featureKey, long currentUsage) {
        if (!isEntitled(organizationId, featureKey, currentUsage)) {
            Plan plan = getCurrentPlan(organizationId);
            long limit = getFeatureLimit(plan, featureKey).orElse(0L);
            throw new QuotaExceededException(featureKey, currentUsage, limit);
        }
    }

    @Override
    public boolean isEntitled(UUID organizationId, String featureKey, long currentUsage) {
        Optional<Subscription> subOpt = subscriptionRepository.findByOrganizationId(organizationId);
        if (subOpt.isPresent()) {
            Subscription sub = subOpt.get();
            if (!sub.isOperable()) {
                log.warn("Organization {} subscription status is {} with grace period end {}, not operable",
                        organizationId, sub.getStatus(), sub.getGracePeriodEnd());
                throw new AutoFlowException(
                        "SUBSCRIPTION_INACTIVE",
                        "Subscription is inactive or payment grace period has expired. Please update your billing details.",
                        HttpStatus.PAYMENT_REQUIRED
                );
            }
            return checkLimit(sub.getPlan(), featureKey, currentUsage);
        }

        // Fallback to FREE plan if no explicit subscription entity exists
        Plan freePlan = getCurrentPlan(organizationId);
        return checkLimit(freePlan, featureKey, currentUsage);
    }

    @Override
    public void assertCanCreateWorkflow(UUID organizationId) {
        long currentCount = workflowRepository.countByOrganizationId(organizationId);
        assertEntitlement(organizationId, "max_automations", currentCount);
    }

    @Override
    public void assertCanConnectChannel(UUID organizationId) {
        long currentChannels = connectedAccountRepository.countByOrganizationIdAndStatus(
                organizationId,
                ConnectedAccountStatus.ACTIVE
        );
        Plan plan = getCurrentPlan(organizationId);
        long channelLimit = getFeatureLimit(plan, "max_channels")
                .orElseGet(() -> DEFAULT_CHANNEL_LIMITS.getOrDefault(plan.getCode(), 1L));

        if (currentChannels >= channelLimit) {
            throw new QuotaExceededException("max_channels", currentChannels, channelLimit);
        }
    }

    @Override
    public void assertCanMakeAiRequest(UUID organizationId, long currentMonthlyAiRequests) {
        assertEntitlement(organizationId, "ai_requests", currentMonthlyAiRequests);
    }

    @Override
    public Plan getCurrentPlan(UUID organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId)
                .map(Subscription::getPlan)
                .orElseGet(() -> planRepository.findByCode("FREE")
                        .orElseGet(() -> Plan.builder()
                                .code("FREE")
                                .name("Free Starter")
                                .build()));
    }

    @Override
    public Subscription getActiveSubscription(UUID organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId).orElse(null);
    }

    private boolean checkLimit(Plan plan, String featureKey, long currentUsage) {
        if (plan == null || plan.getFeatures() == null) {
            return true;
        }

        for (PlanFeature feature : plan.getFeatures()) {
            if (feature.getFeatureKey().equalsIgnoreCase(featureKey)) {
                if (feature.isUnlimited()) {
                    return true;
                }
                return currentUsage < feature.getLimitValue();
            }
        }
        return true;
    }

    private Optional<Long> getFeatureLimit(Plan plan, String featureKey) {
        if (plan == null || plan.getFeatures() == null) {
            return Optional.empty();
        }
        return plan.getFeatures().stream()
                .filter(f -> f.getFeatureKey().equalsIgnoreCase(featureKey))
                .map(PlanFeature::getLimitValue)
                .findFirst();
    }
}
