package com.autoflow.modules.billing.service;

import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.Subscription;

import java.util.UUID;

public interface EntitlementService {

    /**
     * Asserts that the organization is permitted to use the given feature at the given usage level.
     * Throws QuotaExceededException or AutoFlowException if limit is reached or subscription is inactive.
     */
    void assertEntitlement(UUID organizationId, String featureKey, long currentUsage);

    /**
     * Checks if the organization is entitled to the feature without throwing.
     */
    boolean isEntitled(UUID organizationId, String featureKey, long currentUsage);

    /**
     * Validates if a new workflow can be created within plan quotas.
     */
    void assertCanCreateWorkflow(UUID organizationId);

    /**
     * Validates if a new channel connection can be added within plan quotas.
     */
    void assertCanConnectChannel(UUID organizationId);

    /**
     * Validates if an AI prompt request can be made within plan quotas.
     */
    void assertCanMakeAiRequest(UUID organizationId, long currentMonthlyAiRequests);

    /**
     * Gets the currently effective plan for an organization.
     */
    Plan getCurrentPlan(UUID organizationId);

    /**
     * Gets the active subscription or null if on default free tier.
     */
    Subscription getActiveSubscription(UUID organizationId);
}
