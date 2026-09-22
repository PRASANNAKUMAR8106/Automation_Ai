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
import com.autoflow.modules.influencer.entity.*;
import com.autoflow.modules.influencer.repository.CommissionRepository;
import com.autoflow.modules.influencer.repository.PromoCodeRepository;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Billing Service Lifecycle & Subscription Tests")
class BillingServiceTest {

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
        ReflectionTestUtils.setField(billingService, "razorpayKeyId", "rzp_test_mock_key");
        ReflectionTestUtils.setField(billingService, "razorpayWebhookSecret", "rzp_mock_secret");
    }

    @Test
    @DisplayName("Should create checkout order with valid plan and full price when no promo code")
    void shouldCreateCheckoutOrderWithoutPromo() {
        UUID orgId = UUID.randomUUID();
        Plan starterPlan = Plan.builder()
                .code("STARTER")
                .name("Creator Starter")
                .priceInr(BigDecimal.valueOf(499.00))
                .build();
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starterPlan));

        CheckoutRequest request = CheckoutRequest.builder().planCode("STARTER").build();
        CheckoutResponse response = billingService.createCheckoutOrder(orgId, request);

        assertNotNull(response);
        assertNotNull(response.getOrderId());
        assertEquals("STARTER", response.getPlanCode());
        assertEquals(BigDecimal.valueOf(499.00), response.getAmountInr());
        assertEquals(BigDecimal.ZERO, response.getDiscountInr());
        assertEquals(BigDecimal.valueOf(499.00), response.getNetInr());
        assertEquals("INR", response.getCurrency());
    }

    @Test
    @DisplayName("Should apply percentage discount when valid promo code is provided at checkout")
    void shouldApplyPromoCodePercentageDiscount() {
        UUID orgId = UUID.randomUUID();
        Plan proPlan = Plan.builder()
                .code("PRO")
                .name("Pro Marketer")
                .priceInr(BigDecimal.valueOf(1499.00))
                .build();
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(proPlan));

        PromoCode promo = PromoCode.builder()
                .code("SAVE20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20.00))
                .status("ACTIVE")
                .build();
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(promo));

        CheckoutRequest request = CheckoutRequest.builder().planCode("PRO").promoCode("SAVE20").build();
        CheckoutResponse response = billingService.createCheckoutOrder(orgId, request);

        // 20% of 1499.00 = 299.80; Net = 1199.20
        assertEquals(new BigDecimal("299.80"), response.getDiscountInr());
        assertEquals(new BigDecimal("1199.20"), response.getNetInr());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException for invalid plan code")
    void shouldThrowWhenPlanNotFound() {
        UUID orgId = UUID.randomUUID();
        when(planRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        CheckoutRequest request = CheckoutRequest.builder().planCode("INVALID").build();
        assertThrows(ResourceNotFoundException.class, () -> billingService.createCheckoutOrder(orgId, request));
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature")
    void shouldRejectInvalidSignature() {
        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(false);

        assertThrows(AutoFlowException.class, () ->
                billingService.processWebhook("{}", "bad_sig"));
    }

    @Test
    @DisplayName("Should handle subscription.activated and transition subscription to ACTIVE with payment ledger")
    void shouldHandleSubscriptionActivated() {
        UUID orgId = UUID.randomUUID();
        String payload = String.format("""
                {
                    "id": "evt_act_001",
                    "event": "subscription.activated",
                    "payload": {
                        "subscription": {
                            "entity": {
                                "id": "sub_rzp_111",
                                "notes": {
                                    "organization_id": "%s",
                                    "plan_code": "STARTER"
                                }
                            }
                        },
                        "payment": {
                            "entity": {
                                "id": "pay_rzp_111",
                                "amount": 49900
                            }
                        }
                    }
                }
                """, orgId);

        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId(eq("RAZORPAY"), eq("evt_act_001"))).thenReturn(false);
        when(subscriptionRepository.findByProviderSubscriptionId("sub_rzp_111")).thenReturn(Optional.empty());
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());

        Plan starter = Plan.builder().code("STARTER").priceInr(BigDecimal.valueOf(499)).build();
        when(planRepository.findByCode("STARTER")).thenReturn(Optional.of(starter));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
            Subscription s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        WebhookResponse response = billingService.processWebhook(payload, "valid_sig");
        assertEquals("SUCCESS", response.getStatus());

        ArgumentCaptor<Subscription> subCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(subCaptor.capture());
        Subscription savedSub = subCaptor.getValue();
        assertEquals(SubscriptionStatus.ACTIVE, savedSub.getStatus());
        assertEquals("sub_rzp_111", savedSub.getProviderSubscriptionId());
        assertNotNull(savedSub.getCurrentPeriodStart());
        assertNotNull(savedSub.getCurrentPeriodEnd());
        assertNull(savedSub.getGracePeriodEnd());

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("Should handle subscription.charged renewal by advancing current period end by 30 days")
    void shouldHandleSubscriptionChargedRenewal() {
        Instant initialPeriodEnd = Instant.now().plus(5, ChronoUnit.DAYS);
        Subscription existingSub = Subscription.builder()
                .organizationId(UUID.randomUUID())
                .providerSubscriptionId("sub_renew_999")
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodStart(Instant.now().minus(25, ChronoUnit.DAYS))
                .currentPeriodEnd(initialPeriodEnd)
                .build();
        existingSub.setId(UUID.randomUUID());

        String payload = """
                {
                    "id": "evt_charge_001",
                    "event": "subscription.charged",
                    "payload": {
                        "subscription": {
                            "entity": {
                                "id": "sub_renew_999"
                            }
                        },
                        "payment": {
                            "entity": {
                                "id": "pay_renew_999",
                                "amount": 49900
                            }
                        }
                    }
                }
                """;

        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_charge_001")).thenReturn(false);
        when(subscriptionRepository.findByProviderSubscriptionId("sub_renew_999")).thenReturn(Optional.of(existingSub));

        WebhookResponse response = billingService.processWebhook(payload, "valid_sig");
        assertEquals("SUCCESS", response.getStatus());

        verify(subscriptionRepository).save(existingSub);
        assertTrue(existingSub.getCurrentPeriodEnd().isAfter(initialPeriodEnd));
        assertEquals(SubscriptionStatus.ACTIVE, existingSub.getStatus());
        assertNull(existingSub.getGracePeriodEnd());
    }

    @Test
    @DisplayName("Should handle payment.failed by setting status PAST_DUE and granting 7-day grace period")
    void shouldHandlePaymentFailedGracePeriod() {
        Subscription existingSub = Subscription.builder()
                .organizationId(UUID.randomUUID())
                .providerSubscriptionId("sub_fail_123")
                .status(SubscriptionStatus.ACTIVE)
                .build();
        existingSub.setId(UUID.randomUUID());

        String payload = """
                {
                    "id": "evt_fail_001",
                    "event": "payment.failed",
                    "payload": {
                        "subscription": {
                            "entity": {
                                "id": "sub_fail_123"
                            }
                        }
                    }
                }
                """;

        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_fail_001")).thenReturn(false);
        when(subscriptionRepository.findByProviderSubscriptionId("sub_fail_123")).thenReturn(Optional.of(existingSub));

        WebhookResponse response = billingService.processWebhook(payload, "valid_sig");
        assertEquals("SUCCESS", response.getStatus());

        assertEquals(SubscriptionStatus.PAST_DUE, existingSub.getStatus());
        assertNotNull(existingSub.getGracePeriodEnd());
        assertTrue(existingSub.getGracePeriodEnd().isAfter(Instant.now()));
        assertTrue(existingSub.isOperable()); // Operable during grace period!
    }

    @Test
    @DisplayName("Should handle subscription.cancelled by scheduling cancellation at period end")
    void shouldHandleSubscriptionCancelled() {
        Subscription existingSub = Subscription.builder()
                .providerSubscriptionId("sub_cancel_777")
                .status(SubscriptionStatus.ACTIVE)
                .cancelAtPeriodEnd(false)
                .build();
        existingSub.setId(UUID.randomUUID());

        String payload = """
                {
                    "id": "evt_cancel_001",
                    "event": "subscription.cancelled",
                    "payload": {
                        "subscription": {
                            "entity": {
                                "id": "sub_cancel_777"
                            }
                        }
                    }
                }
                """;

        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_cancel_001")).thenReturn(false);
        when(subscriptionRepository.findByProviderSubscriptionId("sub_cancel_777")).thenReturn(Optional.of(existingSub));

        WebhookResponse response = billingService.processWebhook(payload, "valid_sig");
        assertEquals("SUCCESS", response.getStatus());
        assertTrue(existingSub.isCancelAtPeriodEnd());
    }

    @Test
    @DisplayName("Should handle payment.refunded by marking payment refunded and reversing commissions")
    void shouldHandlePaymentRefundedAndCommissionReversal() {
        Payment payment = Payment.builder()
                .id(UUID.randomUUID())
                .providerPaymentId("pay_refund_555")
                .amountInr(BigDecimal.valueOf(1499.00))
                .netInr(BigDecimal.valueOf(1499.00))
                .status("CAPTURED")
                .refunded(false)
                .build();

        Commission commission = Commission.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .amountInr(BigDecimal.valueOf(299.80))
                .status(CommissionStatus.PENDING)
                .build();

        String payload = """
                {
                    "id": "evt_ref_001",
                    "event": "payment.refunded",
                    "payload": {
                        "payment": {
                            "entity": {
                                "id": "pay_refund_555"
                            }
                        },
                        "refund": {
                            "entity": {
                                "amount": 149900
                            }
                        }
                    }
                }
                """;

        when(razorpaySignatureVerifier.verify(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.existsByProviderAndEventId("RAZORPAY", "evt_ref_001")).thenReturn(false);
        when(paymentRepository.findByProviderPaymentId("pay_refund_555")).thenReturn(Optional.of(payment));
        when(commissionRepository.findByPayment(payment)).thenReturn(List.of(commission));

        WebhookResponse response = billingService.processWebhook(payload, "valid_sig");
        assertEquals("SUCCESS", response.getStatus());

        assertTrue(payment.isRefunded());
        assertEquals("REFUNDED", payment.getStatus());
        assertEquals(new BigDecimal("1499.00"), payment.getRefundedAmountInr());

        assertEquals(CommissionStatus.REVERSED, commission.getStatus());
        assertNotNull(commission.getReversedAt());
        assertNotNull(commission.getReversalReason());
    }

    @Test
    @DisplayName("Should cancel subscription immediately when requested")
    void shouldCancelSubscriptionImmediately() {
        UUID orgId = UUID.randomUUID();
        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        sub.setId(UUID.randomUUID());
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        CancelSubscriptionRequest req = new CancelSubscriptionRequest(true);
        SubscriptionResponse resp = billingService.cancelSubscription(orgId, req);

        assertEquals(SubscriptionStatus.CANCELLED.name(), resp.getStatus());
        assertFalse(resp.isOperable());
    }

    @Test
    @DisplayName("Should change plan successfully for existing subscription")
    void shouldChangePlanSuccessfully() {
        UUID orgId = UUID.randomUUID();
        Plan businessPlan = Plan.builder().code("BUSINESS").name("Business Growth").build();
        when(planRepository.findByCode("BUSINESS")).thenReturn(Optional.of(businessPlan));

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(Plan.builder().code("STARTER").name("Starter").build())
                .status(SubscriptionStatus.ACTIVE)
                .build();
        sub.setId(UUID.randomUUID());
        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> i.getArgument(0));

        ChangePlanRequest req = new ChangePlanRequest("BUSINESS");
        SubscriptionResponse resp = billingService.changePlan(orgId, req);

        assertEquals("BUSINESS", resp.getPlanCode());
        assertEquals("Business Growth", resp.getPlanName());
    }
}
