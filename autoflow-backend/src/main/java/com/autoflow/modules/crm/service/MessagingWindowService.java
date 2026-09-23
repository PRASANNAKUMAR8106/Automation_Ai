package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.Conversation;

import java.util.UUID;

public interface MessagingWindowService {

    /**
     * Retrieves the real-time Meta/WhatsApp messaging window compliance status
     * for a conversation belonging to a specific tenant.
     *
     * @param organizationId Organization/Tenant ID
     * @param conversationId Conversation ID
     * @return Compliance window response with remaining seconds and eligibility flags
     */
    CrmDto.MessagingWindowResponse getWindowStatus(UUID organizationId, UUID conversationId);

    /**
     * Evaluates the messaging window status for an already resolved conversation entity.
     *
     * @param conversation Conversation entity
     * @return Compliance window response
     */
    CrmDto.MessagingWindowResponse evaluateWindow(Conversation conversation);

    /**
     * Checks if sending a freeform or tagged message is compliant with channel policies.
     *
     * @param conversation Conversation entity
     * @param hasHumanAgentTag Whether the sender is an authorized human agent using the Meta human agent tag
     * @return true if permitted by channel rules
     */
    boolean isMessageAllowed(Conversation conversation, boolean hasHumanAgentTag);

    /**
     * Validates that an outbound message can be sent, throwing an exception if policy prohibits it.
     *
     * @param conversation Conversation entity
     * @param hasHumanAgentTag Whether human agent tag is active
     */
    void validateCanSend(Conversation conversation, boolean hasHumanAgentTag);
}
