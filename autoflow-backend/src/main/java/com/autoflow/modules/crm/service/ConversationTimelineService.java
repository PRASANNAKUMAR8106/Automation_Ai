package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.TimelineEventDto;

import java.util.List;
import java.util.UUID;

public interface ConversationTimelineService {

    /**
     * Aggregates external messages, internal notes, SLA events, AI actions, and CSAT records
     * into a unified chronological activity timeline.
     */
    List<TimelineEventDto> getConversationTimeline(UUID organizationId, UUID conversationId);
}
