package com.autoflow.modules.billing.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.billing.dto.*;
import com.autoflow.modules.billing.service.BillingService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Billing & Subscriptions", description = "Endpoints for Razorpay checkout, subscription management, webhooks, and entitlement lifecycle")
public class BillingController {

    private final BillingService billingService;

    @PostMapping("/api/v1/billing/checkout")
    @Operation(summary = "Create Checkout Order", description = "Creates a Razorpay checkout order with promo code discount application")
    public ResponseEntity<ApiResponse<CheckoutResponse>> createCheckoutOrder(
            @Valid @RequestBody CheckoutRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        CheckoutResponse response = billingService.createCheckoutOrder(organizationId, request);
        return ResponseEntity.ok(ApiResponse.ok("Checkout order created successfully", response));
    }

    @PostMapping({"/api/v1/billing/webhook/razorpay", "/api/v1/webhooks/razorpay"})
    @Operation(summary = "Process Razorpay Webhook", description = "Verifies HMAC-SHA256 signature and processes subscription events idempotently")
    public ResponseEntity<ApiResponse<WebhookResponse>> processWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature
    ) {
        WebhookResponse response = billingService.processWebhook(payload, signature);
        return ResponseEntity.ok(ApiResponse.ok("Webhook processed", response));
    }

    @GetMapping("/api/v1/billing/subscription")
    @Operation(summary = "Get Organization Subscription", description = "Returns active subscription status, quotas, and current period")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getSubscription() {
        UUID organizationId = TenantContext.getRequiredTenantId();
        SubscriptionResponse response = billingService.getSubscription(organizationId);
        return ResponseEntity.ok(ApiResponse.ok("Subscription retrieved successfully", response));
    }

    @PostMapping("/api/v1/billing/subscription/cancel")
    @Operation(summary = "Cancel Subscription", description = "Cancels subscription either at period end or immediately")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> cancelSubscription(
            @RequestBody(required = false) CancelSubscriptionRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        SubscriptionResponse response = billingService.cancelSubscription(
                organizationId,
                request != null ? request : new CancelSubscriptionRequest(false)
        );
        return ResponseEntity.ok(ApiResponse.ok("Subscription cancelled successfully", response));
    }

    @PostMapping("/api/v1/billing/subscription/change-plan")
    @Operation(summary = "Upgrade or Downgrade Plan", description = "Switches organization subscription to target plan")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> changePlan(
            @Valid @RequestBody ChangePlanRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        SubscriptionResponse response = billingService.changePlan(organizationId, request);
        return ResponseEntity.ok(ApiResponse.ok("Plan changed successfully", response));
    }
}
