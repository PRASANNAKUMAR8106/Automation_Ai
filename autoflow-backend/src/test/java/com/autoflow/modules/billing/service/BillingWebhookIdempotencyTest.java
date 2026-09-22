package com.autoflow.modules.billing.service;

import com.autoflow.modules.billing.dto.WebhookResponse;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.Subscription;
import com.autoflow.modules.billing.entity.SubscriptionStatus;
import com.autoflow.modules.billing.repository.PaymentRepository;
import com.autoflow.modules.billing.repository.PlanRepository;
import com.autoflow.modules.billing.repository.SubscriptionRepository;
import com.autoflow.modules.billing.security.RazorpaySignatureVerifier;
import com.autoflow.modules.influencer.repository.CommissionRepository;
import com.autoflow.modules.influencer.repository.PromoCodeRepository;
import com.autoflow.modules.webhook.entity.WebhookEvent;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Webhook Idempotency & Deduplication Test")
class BillingWebhookIdempotencyTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private WebhookEventRepository webhookEventRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private CommissionRepository commissionRepository;

    @Mock
    private RazorpaySignatureVerifier razorpaySignatureVerifier;

    private BillingServiceImpl billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        billingService = new BillingServiceImpl(
                subscriptionRepository,
                planRepository,
                paymentRepository,
                webhookEventRepository,
                promoCodeRepository,
                commissionRepository,
                razorpaySignatureVerifier,
                objectMapper
        );
        ReflectionTestUtils.setField(billingService, "razorpayKeyId", "rzp_test_123");
        ReflectionTestUtils.setField(billingService, "razorpayWebhookSecret", "secret_123");
    }

    @Test
    @DisplayName("Should process new webhook event and reject duplicate identical event idempotently")
    void shouldProcessOnceAndRejectDuplicate() {
        String eventId = "evt_razorpay_unique_001";
        UUID orgId = UUID.randomUUID();
        String payload = String.format("""
                {
                    "id": "%s",
                    "event": "payment.captured",
                    "payload": {
                        "payment": {
                            "entity": {
                                "id": "pay_test_001",
                                "amount": 149900,
                                "notes": {
                                    "organization_id": "%s",
                                    "plan_code": "PRO"
                                }
                            }
                        }
                    }
                }
                """, eventId, orgId);
        String signature = "valid_signature_abc";

        when(razorpaySignatureVerifier.verify(eq(payload), eq(signature), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", eventId))
                .thenReturn(false) // First call: does not exist
                .thenReturn(true);  // Second call: already exists

        Plan proPlan = Plan.builder().code("PRO").name("Pro Marketer").priceInr(BigDecimal.valueOf(1499)).build();
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        // 1. First execution: should succeed and persist webhook event
        WebhookResponse response1 = billingService.processWebhook(payload, signature);
        assertEquals("SUCCESS", response1.getStatus());
        assertEquals(eventId, response1.getEventId());
        verify(webhookEventRepository, times(1)).save(any(WebhookEvent.class));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));

        // 2. Second execution (Duplicate payload and event ID): should be ignored idempotently without duplicate mutations
        WebhookResponse response2 = billingService.processWebhook(payload, signature);
        assertEquals("IGNORED", response2.getStatus());
        assertEquals(eventId, response2.getEventId());
        assertEquals("Duplicate event ignored", response2.getMessage());

        // Assert no additional subscription or event saves occurred
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }
}
