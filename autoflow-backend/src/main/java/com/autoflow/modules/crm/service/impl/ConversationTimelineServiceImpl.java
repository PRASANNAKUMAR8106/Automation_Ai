package com.autoflow.modules.crm.service.impl;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.TimelineEventDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.*;
import com.autoflow.modules.crm.service.ConversationTimelineService;
import com.autoflow.modules.crm.service.CrmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationTimelineServiceImpl implements ConversationTimelineService {

    private final ConversationRepository conversationRepository;
    private final CrmService crmService;
    private final ConversationInternalNoteRepository internalNoteRepository;
    private final ConversationSlaEventRepository slaEventRepository;
    private final CsatSurveyRepository csatSurveyRepository;
    private final ContactLeadScoreAuditRepository leadScoreAuditRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEventDto> getConversationTimeline(UUID organizationId, UUID conversationId) {
        // Assert conversation exists and belongs to tenant
        conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        List<TimelineEventDto> events = new ArrayList<>();

        // 1. External Messages
        List<Message> messages = crmService.getMessages(organizationId, conversationId);
        for (Message m : messages) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("messageType", m.getMessageType());
            meta.put("deliveryStatus", m.getDeliveryStatus());
            if (m.getMediaUrl() != null) meta.put("mediaUrl", m.getMediaUrl());

            events.add(TimelineEventDto.builder()
                    .id(m.getId())
                    .category("MESSAGE")
                    .eventType(m.getDirection())
                    .summary(m.getContent())
                    .actor(m.getSenderType())
                    .timestamp(m.getSentAt())
                    .metadata(meta)
                    .build());
        }

        // 2. Internal Notes & Whispers
        List<ConversationInternalNote> notes = internalNoteRepository
                .findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(organizationId, conversationId);
        for (ConversationInternalNote n : notes) {
            Map<String, Object> meta = new HashMap<>();
            if (n.getAuthorUserId() != null) meta.put("authorUserId", n.getAuthorUserId().toString());

            events.add(TimelineEventDto.builder()
                    .id(n.getId())
                    .category("INTERNAL_NOTE")
                    .eventType(n.getNoteType())
                    .summary(n.getContent())
                    .actor(n.getAuthorEmail() != null ? n.getAuthorEmail() : "Team Member")
                    .timestamp(n.getCreatedAt())
                    .metadata(meta)
                    .build());
        }

        // 3. Conversation SLA Events
        List<ConversationSlaEvent> slaEvents = slaEventRepository
                .findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(organizationId, conversationId);
        for (ConversationSlaEvent se : slaEvents) {
            Map<String, Object> meta = new HashMap<>();
            if (se.getDetails() != null) meta.put("details", se.getDetails());
            if (se.getAssignedUser() != null) meta.put("assignedUser", se.getAssignedUser().getEmail());
            if (se.getPreviousUser() != null) meta.put("previousUser", se.getPreviousUser().getEmail());
            if (se.getEscalatedToUser() != null) meta.put("escalatedToUser", se.getEscalatedToUser().getEmail());

            events.add(TimelineEventDto.builder()
                    .id(se.getId())
                    .category("SLA_EVENT")
                    .eventType(se.getEventType() != null ? se.getEventType().name() : "EVENT")
                    .summary(se.getReason() != null ? se.getReason() : se.getEventType().name())
                    .actor("SYSTEM")
                    .timestamp(se.getCreatedAt())
                    .metadata(meta)
                    .build());
        }

        // 4. CSAT Survey
        Optional<CsatSurvey> csatOpt = csatSurveyRepository.findByOrganizationIdAndConversationId(organizationId, conversationId);
        if (csatOpt.isPresent()) {
            CsatSurvey csat = csatOpt.get();
            Map<String, Object> meta = new HashMap<>();
            meta.put("status", csat.getStatus() != null ? csat.getStatus().name() : "");
            if (csat.getRating() != null) meta.put("rating", csat.getRating());
            if (csat.getFeedbackText() != null) meta.put("feedback", csat.getFeedbackText());

            String summary = csat.getRating() != null
                    ? "CSAT Rating: " + csat.getRating() + "/5" + (csat.getFeedbackText() != null ? " - \"" + csat.getFeedbackText() + "\"" : "")
                    : "CSAT Survey Dispatched";

            events.add(TimelineEventDto.builder()
                    .id(csat.getId())
                    .category("CSAT")
                    .eventType(csat.getStatus() != null ? csat.getStatus().name() : "CSAT")
                    .summary(summary)
                    .actor("CUSTOMER")
                    .timestamp(csat.getRespondedAt() != null ? csat.getRespondedAt() : csat.getCreatedAt())
                    .metadata(meta)
                    .build());
        }

        // 5. Contact Lead Score Audits
        List<ContactLeadScoreAudit> audits = leadScoreAuditRepository
                .findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(organizationId, conversationId);
        for (ContactLeadScoreAudit audit : audits) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("previousScore", audit.getPreviousScore());
            meta.put("newScore", audit.getNewScore());
            meta.put("delta", audit.getScoreDelta());

            String sign = audit.getScoreDelta() > 0 ? "+" : "";
            String summary = audit.getReason() + " (Delta: " + sign + audit.getScoreDelta() + ", New: " + audit.getNewScore() + ")";

            events.add(TimelineEventDto.builder()
                    .id(audit.getId())
                    .category("LEAD_SCORE")
                    .eventType("SCORE_UPDATE")
                    .summary(summary)
                    .actor("SYSTEM")
                    .timestamp(audit.getCreatedAt())
                    .metadata(meta)
                    .build());
        }

        // Sort ascending by chronological timestamp
        events.sort(Comparator.comparing(TimelineEventDto::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));

        return events;
    }
}
