package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.CrmDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

public interface LiveChatStreamService {

    /**
     * Subscribes a client to real-time events for a specific conversation thread.
     *
     * @param organizationId Tenant ID
     * @param conversationId Conversation ID
     * @return SseEmitter streaming events
     */
    SseEmitter subscribeToConversation(UUID organizationId, UUID conversationId);

    /**
     * Subscribes a client to tenant-wide inbox events (new incoming messages, badges).
     *
     * @param organizationId Tenant ID
     * @return SseEmitter streaming inbox updates
     */
    SseEmitter subscribeToTenantInbox(UUID organizationId);

    /**
     * Broadcasts a new message event to conversation subscribers and tenant inbox subscribers.
     *
     * @param organizationId Tenant ID
     * @param conversationId Conversation ID
     * @param message Message payload
     */
    void broadcastMessage(UUID organizationId, UUID conversationId, CrmDto.MessageResponse message);

    /**
     * Broadcasts an agent or customer typing indicator event.
     *
     * @param organizationId Tenant ID
     * @param conversationId Conversation ID
     * @param isTyping Whether user is typing
     */
    void broadcastTyping(UUID organizationId, UUID conversationId, boolean isTyping);

    /**
     * Broadcasts conversation resolution state change.
     *
     * @param organizationId Tenant ID
     * @param conversationId Conversation ID
     * @param isResolved Resolution state
     */
    void broadcastConversationResolved(UUID organizationId, UUID conversationId, boolean isResolved);
}
