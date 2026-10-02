package com.autoflow.modules.crm.service;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

public interface AiAutopilotService {

    /**
     * Evaluates and conditionally dispatches an automated AI response to an inbound customer message,
     * strictly reusing Phase 19 channel eligibility, persistent consent/suppression, and 24-hour compliance rules.
     *
     * @param organizationId tenant organization ID
     * @param conversationId active conversation ID
     * @return AutopilotExecutionResult detailing dispatch outcome or suppression/escalation reason
     */
    AutopilotExecutionResult processInboundAutoPilot(UUID organizationId, UUID conversationId);

    @Value
    @Builder
    class AutopilotExecutionResult {
        boolean dispatched;
        String status; // DISPATCHED, SKIPPED_SUPPRESSED, SKIPPED_WINDOW_EXPIRED, SKIPPED_HUMAN_HANDOFF, SKIPPED_UNSUPPORTED_CHANNEL
        String reason;
        String messageContent;
        String externalMessageId;
    }
}
