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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Entitlement & Plan Quota Enforcement Tests")
class EntitlementServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private ConnectedAccountRepository connectedAccountRepository;

    private EntitlementServiceImpl entitlementService;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementServiceImpl(
                subscriptionRepository,
                planRepository,
                workflowRepository,
                connectedAccountRepository
        );
    }

    @Test
    @DisplayName("Should permit workflow creation when current count is strictly below plan limit")
    void shouldPermitWorkflowCreationBelowLimit() {
        UUID orgId = UUID.randomUUID();
        PlanFeature automationsFeature = PlanFeature.builder()
                .featureKey("max_automations")
                .limitValue(2L)
                .unlimited(false)
                .build();
        Plan freePlan = Plan.builder()
                .code("FREE")
                .features(List.of(automationsFeature))
                .build();

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(workflowRepository.countByOrganizationId(orgId)).thenReturn(1L);

        assertDoesNotThrow(() -> entitlementService.assertCanCreateWorkflow(orgId));
    }

    @Test
    @DisplayName("Should block workflow creation with QuotaExceededException when limit reached")
    void shouldBlockWorkflowCreationWhenLimitReached() {
        UUID orgId = UUID.randomUUID();
        PlanFeature automationsFeature = PlanFeature.builder()
                .featureKey("max_automations")
                .limitValue(2L)
                .unlimited(false)
                .build();
        Plan freePlan = Plan.builder()
                .code("FREE")
                .features(List.of(automationsFeature))
                .build();

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(workflowRepository.countByOrganizationId(orgId)).thenReturn(2L);

        assertThrows(QuotaExceededException.class, () -> entitlementService.assertCanCreateWorkflow(orgId));
    }

    @Test
    @DisplayName("Should permit operations when feature is flagged as unlimited")
    void shouldPermitUnlimitedFeatures() {
        UUID orgId = UUID.randomUUID();
        PlanFeature unlimitedAutomations = PlanFeature.builder()
                .featureKey("max_automations")
                .unlimited(true)
                .limitValue(0L)
                .build();
        Plan agencyPlan = Plan.builder()
                .code("AGENCY")
                .features(List.of(unlimitedAutomations))
                .build();

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(agencyPlan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(workflowRepository.countByOrganizationId(orgId)).thenReturn(99999L);

        assertDoesNotThrow(() -> entitlementService.assertCanCreateWorkflow(orgId));
    }

    @Test
    @DisplayName("Should permit operations when subscription is in active 7-day grace period")
    void shouldPermitOperationsDuringActiveGracePeriod() {
        UUID orgId = UUID.randomUUID();
        PlanFeature automationsFeature = PlanFeature.builder()
                .featureKey("max_automations")
                .limitValue(5L)
                .unlimited(false)
                .build();
        Plan starterPlan = Plan.builder()
                .code("STARTER")
                .features(List.of(automationsFeature))
                .build();

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(starterPlan)
                .status(SubscriptionStatus.PAST_DUE)
                .gracePeriodEnd(Instant.now().plus(4, ChronoUnit.DAYS))
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        when(workflowRepository.countByOrganizationId(orgId)).thenReturn(2L);

        // Operable during grace period!
        assertDoesNotThrow(() -> entitlementService.assertCanCreateWorkflow(orgId));
    }

    @Test
    @DisplayName("Should reject operations with AutoFlowException when grace period has expired")
    void shouldRejectOperationsWhenGracePeriodExpired() {
        UUID orgId = UUID.randomUUID();
        Plan starterPlan = Plan.builder().code("STARTER").build();

        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(starterPlan)
                .status(SubscriptionStatus.PAST_DUE)
                .gracePeriodEnd(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));

        AutoFlowException ex = assertThrows(AutoFlowException.class, () ->
                entitlementService.assertCanCreateWorkflow(orgId));
        assertEquals("SUBSCRIPTION_INACTIVE", ex.getErrorCode());
    }

    @Test
    @DisplayName("Should enforce social channel connection quota")
    void shouldEnforceChannelConnectionQuota() {
        UUID orgId = UUID.randomUUID();
        Plan freePlan = Plan.builder().code("FREE").features(List.of()).build();
        Subscription sub = Subscription.builder()
                .organizationId(orgId)
                .plan(freePlan)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(subscriptionRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(sub));
        // FREE plan default channel limit is 1. If org already has 1 active channel, adding another must throw.
        when(connectedAccountRepository.countByOrganizationIdAndStatus(orgId, ConnectedAccountStatus.ACTIVE))
                .thenReturn(1L);

        assertThrows(QuotaExceededException.class, () -> entitlementService.assertCanConnectChannel(orgId));
    }
}
