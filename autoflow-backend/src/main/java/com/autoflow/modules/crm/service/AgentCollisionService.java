package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.AgentPresenceDto;

import java.util.List;
import java.util.UUID;

public interface AgentCollisionService {

    /**
     * Records or updates viewing/typing presence for an agent on a conversation.
     * Broadcasts updated presence list via SSE.
     */
    List<AgentPresenceDto> recordPresence(UUID organizationId, UUID conversationId, UUID userId, String userEmail, String action);

    /**
     * Explicitly releases presence when an agent navigates away from a conversation.
     */
    List<AgentPresenceDto> releasePresence(UUID organizationId, UUID conversationId, UUID userId);

    /**
     * Returns all currently active agents viewing or typing in the conversation within the 30s TTL lease.
     */
    List<AgentPresenceDto> getActiveViewers(UUID organizationId, UUID conversationId);
}
