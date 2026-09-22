package com.autoflow.modules.billing.service;

import com.autoflow.modules.billing.dto.*;

import java.util.UUID;

public interface BillingService {

    /**
     * Prepares a Razorpay checkout order with promo code discount application and pricing precision.
     */
    CheckoutResponse createCheckoutOrder(UUID organizationId, CheckoutRequest request);

    /**
     * Processes inbound webhooks from Razorpay with HMAC-SHA256 signature verification
     * and strict database-level idempotency protection.
     */
    WebhookResponse processWebhook(String payload, String signature);

    /**
     * Retrieves the current subscription details for an organization.
     */
    SubscriptionResponse getSubscription(UUID organizationId);

    /**
     * Cancels an active subscription either immediately or at period end.
     */
    SubscriptionResponse cancelSubscription(UUID organizationId, CancelSubscriptionRequest request);

    /**
     * Upgrades or downgrades the subscription plan for an organization.
     */
    SubscriptionResponse changePlan(UUID organizationId, ChangePlanRequest request);
}
