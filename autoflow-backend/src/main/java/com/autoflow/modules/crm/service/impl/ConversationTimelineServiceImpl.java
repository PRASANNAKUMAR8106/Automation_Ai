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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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
            attachAuditLedger(meta, organizationId, conversationId, m.getSentAt());

            String checksum = computeAuditChecksum(organizationId, conversationId, m.getId(), "MESSAGE", m.getDirection(), m.getSentAt());

            events.add(TimelineEventDto.builder()
                    .id(m.getId())
                    .category("MESSAGE")
                    .eventType(m.getDirection())
                    .summary(m.getContent())
                    .actor(m.getSenderType())
                    .timestamp(m.getSentAt())
                    .metadata(meta)
                    .auditChecksum(checksum)
                    .immutableAudit(true)
                    .build());
        }

        // 2. Internal Notes & Whispers
        List<ConversationInternalNote> notes = internalNoteRepository
                .findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(organizationId, conversationId);
        for (ConversationInternalNote n : notes) {
            Map<String, Object> meta = new HashMap<>();
            if (n.getAuthorUserId() != null) meta.put("authorUserId", n.getAuthorUserId().toString());
            attachAuditLedger(meta, organizationId, conversationId, n.getCreatedAt());

            String checksum = computeAuditChecksum(organizationId, conversationId, n.getId(), "INTERNAL_NOTE", n.getNoteType(), n.getCreatedAt());

            events.add(TimelineEventDto.builder()
                    .id(n.getId())
                    .category("INTERNAL_NOTE")
                    .eventType(n.getNoteType())
                    .summary(n.getContent())
                    .actor(n.getAuthorEmail() != null ? n.getAuthorEmail() : "Team Member")
                    .timestamp(n.getCreatedAt())
                    .metadata(meta)
                    .auditChecksum(checksum)
                    .immutableAudit(true)
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
            attachAuditLedger(meta, organizationId, conversationId, se.getCreatedAt());

            String eventTypeStr = se.getEventType() != null ? se.getEventType().name() : "EVENT";
            String checksum = computeAuditChecksum(organizationId, conversationId, se.getId(), "SLA_EVENT", eventTypeStr, se.getCreatedAt());

            events.add(TimelineEventDto.builder()
                    .id(se.getId())
                    .category("SLA_EVENT")
                    .eventType(eventTypeStr)
                    .summary(se.getReason() != null ? se.getReason() : eventTypeStr)
                    .actor("SYSTEM")
                    .timestamp(se.getCreatedAt())
                    .metadata(meta)
                    .auditChecksum(checksum)
                    .immutableAudit(true)
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
            Instant csatTs = csat.getRespondedAt() != null ? csat.getRespondedAt() : csat.getCreatedAt();
            attachAuditLedger(meta, organizationId, conversationId, csatTs);

            String summary = csat.getRating() != null
                    ? "CSAT Rating: " + csat.getRating() + "/5" + (csat.getFeedbackText() != null ? " - \"" + csat.getFeedbackText() + "\"" : "")
                    : "CSAT Survey Dispatched";

            String eventTypeStr = csat.getStatus() != null ? csat.getStatus().name() : "CSAT";
            String checksum = computeAuditChecksum(organizationId, conversationId, csat.getId(), "CSAT", eventTypeStr, csatTs);

            events.add(TimelineEventDto.builder()
                    .id(csat.getId())
                    .category("CSAT")
                    .eventType(eventTypeStr)
                    .summary(summary)
                    .actor("CUSTOMER")
                    .timestamp(csatTs)
                    .metadata(meta)
                    .auditChecksum(checksum)
                    .immutableAudit(true)
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
            attachAuditLedger(meta, organizationId, conversationId, audit.getCreatedAt());

            String sign = audit.getScoreDelta() > 0 ? "+" : "";
            String summary = audit.getReason() + " (Delta: " + sign + audit.getScoreDelta() + ", New: " + audit.getNewScore() + ")";

            String checksum = computeAuditChecksum(organizationId, conversationId, audit.getId(), "LEAD_SCORE", "SCORE_UPDATE", audit.getCreatedAt());

            events.add(TimelineEventDto.builder()
                    .id(audit.getId())
                    .category("LEAD_SCORE")
                    .eventType("SCORE_UPDATE")
                    .summary(summary)
                    .actor("SYSTEM")
                    .timestamp(audit.getCreatedAt())
                    .metadata(meta)
                    .auditChecksum(checksum)
                    .immutableAudit(true)
                    .build());
        }

        // Sort ascending by chronological timestamp
        events.sort(Comparator.comparing(TimelineEventDto::getTimestamp, Comparator.nullsLast(Comparator.naturalOrder())));

        return events;
    }

    private void attachAuditLedger(Map<String, Object> meta, UUID organizationId, UUID conversationId, Instant ts) {
        meta.put("auditLedger", Map.of(
                "tenantId", organizationId.toString(),
                "conversationId", conversationId.toString(),
                "recordedAt", ts != null ? ts.toString() : Instant.now().toString(),
                "immutable", true
        ));
    }

    private String computeAuditChecksum(UUID orgId, UUID convId, UUID eventId, String category, String eventType, Instant ts) {
        try {
            String raw = orgId + ":" + convId + ":" + eventId + ":" + category + ":" + eventType + ":" + (ts != null ? ts.toEpochMilli() : 0);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "sha256:fallback:" + UUID.randomUUID();
        }
    }
}
