package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import com.autoflow.modules.user.entity.User;

import java.util.UUID;

public interface ConversationRoutingService {

    /**
     * Determines and assigns an active agent to the conversation based on the specified routing policy.
     */
    User assignConversation(Conversation conversation, RoutingPolicy routingPolicy);

    /**
     * Determines and assigns an active agent to the conversation by ID.
     */
    User assignConversation(UUID organizationId, UUID conversationId, RoutingPolicy routingPolicy);
}
