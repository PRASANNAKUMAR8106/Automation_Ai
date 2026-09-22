package com.autoflow.modules.billing;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.billing.controller.BillingController;
import com.autoflow.modules.billing.dto.*;
import com.autoflow.modules.billing.service.BillingService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = BillingController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("BillingController Web MVC Tests")
class BillingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BillingService billingService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/billing/checkout creates Razorpay order")
    void testCreateCheckoutOrder() throws Exception {
        CheckoutRequest request = CheckoutRequest.builder().planCode("STARTER").promoCode("SAVE10").build();
        CheckoutResponse response = CheckoutResponse.builder()
                .orderId("order_test_123456")
                .amountInr(BigDecimal.valueOf(499.00))
                .discountInr(BigDecimal.valueOf(49.90))
                .netInr(BigDecimal.valueOf(449.10))
                .currency("INR")
                .keyId("rzp_test_key")
                .planCode("STARTER")
                .planName("Creator Starter")
                .build();

        when(billingService.createCheckoutOrder(eq(testOrgId), any(CheckoutRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/billing/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderId").value("order_test_123456"))
                .andExpect(jsonPath("$.data.planCode").value("STARTER"))
                .andExpect(jsonPath("$.data.netInr").value(449.10));
    }

    @Test
    @DisplayName("POST /api/v1/billing/webhook/razorpay processes webhook and returns success")
    void testProcessWebhook() throws Exception {
        String payload = "{\"event\":\"payment.captured\"}";
        String signature = "test_signature_xyz";
        WebhookResponse response = WebhookResponse.builder()
                .status("SUCCESS")
                .eventId("evt_12345")
                .message("Successfully processed payment.captured")
                .build();

        when(billingService.processWebhook(eq(payload), eq(signature))).thenReturn(response);

        mockMvc.perform(post("/api/v1/billing/webhook/razorpay")
                        .header("X-Razorpay-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.eventId").value("evt_12345"));
    }

    @Test
    @DisplayName("GET /api/v1/billing/subscription returns current organization subscription")
    void testGetSubscription() throws Exception {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .id(UUID.randomUUID())
                .organizationId(testOrgId)
                .planCode("PRO")
                .planName("Pro Marketer")
                .status("ACTIVE")
                .operable(true)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(2592000L))
                .build();

        when(billingService.getSubscription(testOrgId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/billing/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.planCode").value("PRO"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.operable").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/billing/subscription/cancel cancels subscription")
    void testCancelSubscription() throws Exception {
        CancelSubscriptionRequest request = new CancelSubscriptionRequest(false);
        SubscriptionResponse response = SubscriptionResponse.builder()
                .id(UUID.randomUUID())
                .organizationId(testOrgId)
                .planCode("PRO")
                .status("ACTIVE")
                .cancelAtPeriodEnd(true)
                .operable(true)
                .build();

        when(billingService.cancelSubscription(eq(testOrgId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/billing/subscription/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/billing/subscription/change-plan updates plan")
    void testChangePlan() throws Exception {
        ChangePlanRequest request = new ChangePlanRequest("AGENCY");
        SubscriptionResponse response = SubscriptionResponse.builder()
                .id(UUID.randomUUID())
                .organizationId(testOrgId)
                .planCode("AGENCY")
                .status("ACTIVE")
                .operable(true)
                .build();

        when(billingService.changePlan(eq(testOrgId), any(ChangePlanRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/billing/subscription/change-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.planCode").value("AGENCY"));
    }
}
