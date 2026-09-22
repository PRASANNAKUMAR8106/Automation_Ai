package com.autoflow.modules.billing.service;

import com.autoflow.common.exceptions.AutoFlowException;
import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.billing.dto.*;
import com.autoflow.modules.billing.entity.Payment;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.Subscription;
import com.autoflow.modules.billing.entity.SubscriptionStatus;
import com.autoflow.modules.billing.repository.PaymentRepository;
import com.autoflow.modules.billing.repository.PlanRepository;
import com.autoflow.modules.billing.repository.SubscriptionRepository;
import com.autoflow.modules.billing.security.RazorpaySignatureVerifier;
import com.autoflow.modules.influencer.entity.Commission;
import com.autoflow.modules.influencer.entity.CommissionStatus;
import com.autoflow.modules.influencer.entity.DiscountType;
import com.autoflow.modules.influencer.entity.PromoCode;
import com.autoflow.modules.influencer.repository.CommissionRepository;
import com.autoflow.modules.influencer.repository.PromoCodeRepository;
import com.autoflow.modules.webhook.entity.WebhookEvent;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final CommissionRepository commissionRepository;
    private final RazorpaySignatureVerifier razorpaySignatureVerifier;
    private final ObjectMapper objectMapper;

    @Value("${autoflow.razorpay.key-id:rzp_test_placeholder}")
    private String razorpayKeyId;

    @Value("${autoflow.razorpay.webhook-secret:webhook_secret_placeholder}")
    private String razorpayWebhookSecret;

    @Override
    @Transactional
    public CheckoutResponse createCheckoutOrder(UUID organizationId, CheckoutRequest request) {
        Plan plan = planRepository.findByCode(request.getPlanCode().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", request.getPlanCode()));

        BigDecimal originalPrice = plan.getPriceInr();
        BigDecimal discountInr = BigDecimal.ZERO;

        if (request.getPromoCode() != null && !request.getPromoCode().trim().isEmpty()) {
            String code = request.getPromoCode().trim();
            Optional<PromoCode> promoOpt = promoCodeRepository.findByCodeIgnoreCase(code);
            if (promoOpt.isPresent()) {
                PromoCode promo = promoOpt.get();
                if (promo.isValidForCheckout(originalPrice)) {
                    if (promo.getDiscountType() == DiscountType.PERCENTAGE) {
                        discountInr = originalPrice.multiply(promo.getDiscountValue())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else {
                        discountInr = promo.getDiscountValue().min(originalPrice);
                    }
                }
            }
        }

        BigDecimal netInr = originalPrice.subtract(discountInr).max(BigDecimal.ZERO);
        String orderId = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);

        log.info("Created checkout order {} for org {} on plan {} (Price: {}, Net: {})",
                orderId, organizationId, plan.getCode(), originalPrice, netInr);

        return CheckoutResponse.builder()
                .orderId(orderId)
                .amountInr(originalPrice)
                .discountInr(discountInr)
                .netInr(netInr)
                .currency("INR")
                .keyId(razorpayKeyId)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .build();
    }

    @Override
    @Transactional
    public WebhookResponse processWebhook(String payload, String signature) {
        if (!razorpaySignatureVerifier.verify(payload, signature, razorpayWebhookSecret)) {
            log.warn("Invalid Razorpay webhook signature provided");
            throw new AutoFlowException("INVALID_SIGNATURE", "Razorpay webhook signature verification failed", HttpStatus.BAD_REQUEST);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (Exception e) {
            log.error("Failed to parse Razorpay webhook payload JSON", e);
            throw new AutoFlowException("INVALID_PAYLOAD", "Malformed webhook payload", HttpStatus.BAD_REQUEST);
        }

        String event = root.path("event").asText("");
        String eventId = extractEventId(root, payload);

        // Strict Idempotency Check
        if (webhookEventRepository.existsByProviderAndEventId("RAZORPAY", eventId)) {
            log.info("Duplicate webhook event {} already processed, skipping", eventId);
            return WebhookResponse.builder()
                    .status("IGNORED")
                    .eventId(eventId)
                    .message("Duplicate event ignored")
                    .build();
        }

        // Save ledger entry before state mutation
        WebhookEvent webhookRecord = WebhookEvent.builder()
                .provider("RAZORPAY")
                .eventId(eventId)
                .payload(payload)
                .build();
        webhookEventRepository.save(webhookRecord);

        // Dispatch based on event
        switch (event) {
            case "subscription.activated", "subscription.authenticated", "payment.captured", "order.paid" ->
                    handleSubscriptionActivated(root);
            case "subscription.charged" ->
                    handleSubscriptionCharged(root);
            case "payment.failed", "subscription.halted" ->
                    handlePaymentFailed(root);
            case "subscription.cancelled" ->
                    handleSubscriptionCancelled(root);
            case "payment.refunded" ->
                    handlePaymentRefunded(root);
            default ->
                    log.info("Unhandled Razorpay webhook event: {}", event);
        }

        return WebhookResponse.builder()
                .status("SUCCESS")
                .eventId(eventId)
                .message("Successfully processed " + event)
                .build();
    }

    private void handleSubscriptionActivated(JsonNode root) {
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        JsonNode subscriptionEntity = root.path("payload").path("subscription").path("entity");
        JsonNode notes = paymentEntity.hasNonNull("notes") ? paymentEntity.path("notes") : subscriptionEntity.path("notes");

        String orgIdStr = notes.path("organization_id").asText(null);
        UUID orgId = parseUuidSafe(orgIdStr);

        String subId = subscriptionEntity.hasNonNull("id") ? subscriptionEntity.path("id").asText() : null;
        Subscription sub = null;

        if (subId != null && !subId.isBlank()) {
            sub = subscriptionRepository.findByProviderSubscriptionId(subId).orElse(null);
        }
        if (sub == null && orgId != null) {
            sub = subscriptionRepository.findByOrganizationId(orgId).orElse(null);
        }

        String planCode = notes.path("plan_code").asText("STARTER");
        Plan plan = planRepository.findByCode(planCode.toUpperCase())
                .orElseGet(() -> planRepository.findByCode("STARTER")
                        .orElse(null));

        if (sub == null && orgId != null) {
            sub = Subscription.builder()
                    .organizationId(orgId)
                    .plan(plan)
                    .provider("RAZORPAY")
                    .providerSubscriptionId(subId)
                    .build();
        }

        if (sub != null) {
            if (plan != null) sub.setPlan(plan);
            if (subId != null) sub.setProviderSubscriptionId(subId);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setCurrentPeriodStart(Instant.now());
            sub.setCurrentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS));
            sub.setGracePeriodEnd(null);
            sub.setCancelAtPeriodEnd(false);
            sub = subscriptionRepository.save(sub);

            // Record payment
            recordPaymentIfPresent(paymentEntity, sub.getOrganizationId(), sub.getId());
            log.info("Subscription activated for org {}: plan {}", sub.getOrganizationId(), sub.getPlan().getCode());
        }
    }

    private void handleSubscriptionCharged(JsonNode root) {
        JsonNode subEntity = root.path("payload").path("subscription").path("entity");
        String subId = subEntity.path("id").asText(null);

        Subscription sub = null;
        if (subId != null) {
            sub = subscriptionRepository.findByProviderSubscriptionId(subId).orElse(null);
        }

        if (sub == null) {
            String orgIdStr = subEntity.path("notes").path("organization_id").asText(null);
            UUID orgId = parseUuidSafe(orgIdStr);
            if (orgId != null) {
                sub = subscriptionRepository.findByOrganizationId(orgId).orElse(null);
            }
        }

        if (sub != null) {
            Instant currentEnd = sub.getCurrentPeriodEnd();
            Instant newEnd = (currentEnd != null && currentEnd.isAfter(Instant.now()))
                    ? currentEnd.plus(30, ChronoUnit.DAYS)
                    : Instant.now().plus(30, ChronoUnit.DAYS);

            sub.setCurrentPeriodEnd(newEnd);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setGracePeriodEnd(null);
            subscriptionRepository.save(sub);

            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            recordPaymentIfPresent(paymentEntity, sub.getOrganizationId(), sub.getId());
            log.info("Subscription renewed for org {}. New period end: {}", sub.getOrganizationId(), newEnd);
        }
    }

    private void handlePaymentFailed(JsonNode root) {
        JsonNode subEntity = root.path("payload").path("subscription").path("entity");
        String subId = subEntity.path("id").asText(null);

        Subscription sub = null;
        if (subId != null) {
            sub = subscriptionRepository.findByProviderSubscriptionId(subId).orElse(null);
        }

        if (sub == null) {
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            String orgIdStr = paymentEntity.path("notes").path("organization_id").asText(null);
            UUID orgId = parseUuidSafe(orgIdStr);
            if (orgId != null) {
                sub = subscriptionRepository.findByOrganizationId(orgId).orElse(null);
            }
        }

        if (sub != null) {
            sub.setStatus(SubscriptionStatus.PAST_DUE);
            // 7-day grace period
            sub.setGracePeriodEnd(Instant.now().plus(7, ChronoUnit.DAYS));
            subscriptionRepository.save(sub);
            log.warn("Payment failed for subscription {}. Placed in 7-day grace period until {}",
                    sub.getId(), sub.getGracePeriodEnd());
        }
    }

    private void handleSubscriptionCancelled(JsonNode root) {
        JsonNode subEntity = root.path("payload").path("subscription").path("entity");
        String subId = subEntity.path("id").asText(null);

        Subscription sub = null;
        if (subId != null) {
            sub = subscriptionRepository.findByProviderSubscriptionId(subId).orElse(null);
        }

        if (sub != null) {
            sub.setCancelAtPeriodEnd(true);
            subscriptionRepository.save(sub);
            log.info("Subscription {} scheduled to cancel at period end {}", sub.getId(), sub.getCurrentPeriodEnd());
        }
    }

    private void handlePaymentRefunded(JsonNode root) {
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String paymentId = paymentEntity.path("id").asText(null);

        if (paymentId != null) {
            paymentRepository.findByProviderPaymentId(paymentId).ifPresent(payment -> {
                payment.setRefunded(true);
                payment.setStatus("REFUNDED");

                long refundAmountPaise = root.path("payload").path("refund").path("entity").path("amount").asLong(0);
                if (refundAmountPaise > 0) {
                    payment.setRefundedAmountInr(BigDecimal.valueOf(refundAmountPaise)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                } else {
                    payment.setRefundedAmountInr(payment.getNetInr());
                }
                paymentRepository.save(payment);

                // Reverse any influencer commissions associated with this payment
                List<Commission> commissions = commissionRepository.findByPayment(payment);
                for (Commission comm : commissions) {
                    if (comm.getStatus() != CommissionStatus.REVERSED) {
                        comm.setStatus(CommissionStatus.REVERSED);
                        comm.setReversedAt(Instant.now());
                        comm.setReversalReason("Payment refunded by customer/provider");
                        commissionRepository.save(comm);
                        log.info("Reversed commission {} due to payment refund", comm.getId());
                    }
                }

                log.info("Processed refund for payment {}: refunded INR {}", paymentId, payment.getRefundedAmountInr());
            });
        }
    }

    private void recordPaymentIfPresent(JsonNode paymentEntity, UUID orgId, UUID subId) {
        if (paymentEntity != null && paymentEntity.hasNonNull("id")) {
            String paymentId = paymentEntity.path("id").asText();
            if (!paymentRepository.existsByProviderPaymentId(paymentId)) {
                long amountPaise = paymentEntity.path("amount").asLong(0);
                BigDecimal amountInr = BigDecimal.valueOf(amountPaise)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String orderId = paymentEntity.path("order_id").asText(null);

                Payment payment = Payment.builder()
                        .organizationId(orgId)
                        .subscriptionId(subId)
                        .provider("RAZORPAY")
                        .providerPaymentId(paymentId)
                        .providerOrderId(orderId)
                        .amountInr(amountInr)
                        .netInr(amountInr)
                        .status("CAPTURED")
                        .build();
                paymentRepository.save(payment);
                log.info("Recorded payment {} for org {} (Amount: INR {})", paymentId, orgId, amountInr);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(UUID organizationId) {
        return subscriptionRepository.findByOrganizationId(organizationId)
                .map(SubscriptionResponse::fromEntity)
                .orElseGet(() -> {
                    Plan freePlan = planRepository.findByCode("FREE")
                            .orElseGet(() -> Plan.builder().code("FREE").name("Free Starter").build());
                    return SubscriptionResponse.builder()
                            .organizationId(organizationId)
                            .planCode(freePlan.getCode())
                            .planName(freePlan.getName())
                            .status(SubscriptionStatus.ACTIVE.name())
                            .operable(true)
                            .cancelAtPeriodEnd(false)
                            .build();
                });
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(UUID organizationId, CancelSubscriptionRequest request) {
        Subscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", organizationId));

        if (request != null && request.isImmediate()) {
            sub.setStatus(SubscriptionStatus.CANCELLED);
        } else {
            sub.setCancelAtPeriodEnd(true);
        }

        sub = subscriptionRepository.save(sub);
        log.info("Cancelled subscription for org {}: immediate={}", organizationId, request != null && request.isImmediate());
        return SubscriptionResponse.fromEntity(sub);
    }

    @Override
    @Transactional
    public SubscriptionResponse changePlan(UUID organizationId, ChangePlanRequest request) {
        Plan targetPlan = planRepository.findByCode(request.getTargetPlanCode().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Plan", request.getTargetPlanCode()));

        Subscription sub = subscriptionRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> Subscription.builder()
                        .organizationId(organizationId)
                        .status(SubscriptionStatus.ACTIVE)
                        .currentPeriodStart(Instant.now())
                        .currentPeriodEnd(Instant.now().plus(30, ChronoUnit.DAYS))
                        .build());

        sub.setPlan(targetPlan);
        sub = subscriptionRepository.save(sub);
        log.info("Changed subscription plan for org {} to {}", organizationId, targetPlan.getCode());
        return SubscriptionResponse.fromEntity(sub);
    }

    private String extractEventId(JsonNode root, String rawPayload) {
        if (root.hasNonNull("id")) {
            return root.get("id").asText();
        }
        String event = root.path("event").asText("");
        String entityId = root.path("payload").path("payment").path("entity").path("id").asText("");
        if (entityId.isEmpty()) {
            entityId = root.path("payload").path("subscription").path("entity").path("id").asText("");
        }
        if (!event.isEmpty() && !entityId.isEmpty()) {
            return event + "_" + entityId;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPayload.getBytes(StandardCharsets.UTF_8));
            return "evt_" + HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException e) {
            return "evt_" + UUID.randomUUID().toString().replace("-", "");
        }
    }

    private UUID parseUuidSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
