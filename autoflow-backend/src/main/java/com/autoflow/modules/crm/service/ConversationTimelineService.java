package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.TimelineEventDto;

import java.util.List;
import java.util.UUID;

public interface ConversationTimelineService {

    /**
     * Aggregates external messages, internal notes, SLA events, AI actions, and CSAT records
     * into a unified chronological activity timeline with tamper-evident cryptographic checksums.
     */
    List<TimelineEventDto> getConversationTimeline(UUID organizationId, UUID conversationId);

    /**
     * Verifies the tamper-evident cryptographic SHA-256 audit checksum of a timeline event
     * against its canonical representation.
     */
    boolean verifyTimelineEventIntegrity(TimelineEventDto event, UUID organizationId, UUID conversationId);
}
