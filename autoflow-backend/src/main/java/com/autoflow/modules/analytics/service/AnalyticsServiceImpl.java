package com.autoflow.modules.analytics.service;

import com.autoflow.modules.analytics.dto.AnalyticsOverviewResponse;
import com.autoflow.modules.billing.entity.Plan;
import com.autoflow.modules.billing.entity.PlanFeature;
import com.autoflow.modules.billing.service.EntitlementService;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import com.autoflow.modules.workflow.repository.AutomationExecutionRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AutomationExecutionRepository automationExecutionRepository;
    private final MessageRepository messageRepository;
    private final ContactRepository contactRepository;
    private final WorkflowRepository workflowRepository;
    private final EntitlementService entitlementService;

    @Override
    @Transactional(readOnly = true)
    public AnalyticsOverviewResponse getOverview(UUID organizationId) {
        long comments = automationExecutionRepository.countByOrganizationIdAndTriggerType(organizationId, "TRIGGER_INSTAGRAM_COMMENT");
        long outboundDms = messageRepository.countByOrganizationIdAndDirection(organizationId, "OUTBOUND");
        long inboundMsgs = messageRepository.countByOrganizationIdAndDirection(organizationId, "INBOUND");
        long totalMsgs = messageRepository.countByOrganizationId(organizationId);
        long leads = contactRepository.countByOrganizationId(organizationId);
        long activeWorkflows = workflowRepository.countByOrganizationIdAndStatus(organizationId, "PUBLISHED");

        int maxWorkflows = 20; // Default
        try {
            Plan plan = entitlementService.getCurrentPlan(organizationId);
            if (plan != null && plan.getFeatures() != null) {
                for (PlanFeature feature : plan.getFeatures()) {
                    if ("max_automations".equalsIgnoreCase(feature.getFeatureKey())) {
                        maxWorkflows = feature.isUnlimited() ? 9999 : feature.getLimitValue().intValue();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not calculate maxWorkflows entitlement: {}", e.getMessage());
        }

        // Channel breakdown
        Map<String, Long> breakdown = new HashMap<>();
        List<Contact> contacts = contactRepository.findByOrganizationId(organizationId);
        for (Contact c : contacts) {
            if (c.getChannel() != null) {
                breakdown.merge(c.getChannel().name(), 1L, Long::sum);
            }
        }
        if (breakdown.isEmpty()) {
            breakdown.put("INSTAGRAM", 0L);
            breakdown.put("WHATSAPP", 0L);
        }

        return AnalyticsOverviewResponse.builder()
                .commentsProcessed(comments)
                .dmsDispatched(outboundDms)
                .inboundMessages(inboundMsgs)
                .outboundMessages(outboundDms)
                .totalMessages(totalMsgs)
                .leadsCaptured(leads)
                .activeWorkflows(activeWorkflows)
                .maxWorkflows(maxWorkflows)
                .channelBreakdown(breakdown)
                .build();
    }
}
