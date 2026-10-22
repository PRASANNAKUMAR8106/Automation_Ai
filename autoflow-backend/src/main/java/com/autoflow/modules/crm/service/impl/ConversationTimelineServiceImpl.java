package com.autoflow.modules.crm.service.impl;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.TimelineEventDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.*;
import com.autoflow.modules.crm.service.ConversationTimelineService;
import com.autoflow.modules.crm.service.CrmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autoflow.modules.crm.service.AuditChecksumUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class ConversationTimelineServiceImpl implements ConversationTimelineService {

    private final ConversationRepository conversationRepository;
    private final CrmService crmService;
    private final ConversationInternalNoteRepository internalNoteRepository;
    private final ConversationSlaEventRepository slaEventRepository;
    private final CsatSurveyRepository csatSurveyRepository;
    private final ContactLeadScoreAuditRepository leadScoreAuditRepository;
    @Nullable
    private final MessageRepository messageRepository;

    public ConversationTimelineServiceImpl(
            ConversationRepository conversationRepository,
            CrmService crmService,
            ConversationInternalNoteRepository internalNoteRepository,
            ConversationSlaEventRepository slaEventRepository,
            CsatSurveyRepository csatSurveyRepository,
            ContactLeadScoreAuditRepository leadScoreAuditRepository
    ) {
        this(conversationRepository, crmService, internalNoteRepository, slaEventRepository, csatSurveyRepository, leadScoreAuditRepository, null);
    }

    @Autowired
    public ConversationTimelineServiceImpl(
            ConversationRepository conversationRepository,
            CrmService crmService,
            ConversationInternalNoteRepository internalNoteRepository,
            ConversationSlaEventRepository slaEventRepository,
            CsatSurveyRepository csatSurveyRepository,
            ContactLeadScoreAuditRepository leadScoreAuditRepository,
            @Autowired(required = false) @Nullable MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.crmService = crmService;
        this.internalNoteRepository = internalNoteRepository;
        this.slaEventRepository = slaEventRepository;
        this.csatSurveyRepository = csatSurveyRepository;
        this.leadScoreAuditRepository = leadScoreAuditRepository;
        this.messageRepository = messageRepository;
    }

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
            attachAuditLedger(meta, organizationId, conversationId, m.getSentAt(), "MESSAGE");

            String actor = m.getSenderType();
            String summary = m.getContent();
            String checksum = m.getAuditChecksum() != null ? m.getAuditChecksum()
                    : AuditChecksumUtils.computeMessageChecksum(organizationId, conversationId, m);

            events.add(TimelineEventDto.builder()
                    .id(m.getId())
                    .category("MESSAGE")
                    .eventType(m.getDirection())
                    .summary(summary)
                    .actor(actor)
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
            attachAuditLedger(meta, organizationId, conversationId, n.getCreatedAt(), "INTERNAL_NOTE");

            String actor = n.getAuthorEmail() != null ? n.getAuthorEmail() : "Team Member";
            String summary = n.getContent();
            String checksum = n.getAuditChecksum() != null ? n.getAuditChecksum()
                    : AuditChecksumUtils.computeInternalNoteChecksum(organizationId, conversationId, n);

            events.add(TimelineEventDto.builder()
                    .id(n.getId())
                    .category("INTERNAL_NOTE")
                    .eventType(n.getNoteType())
                    .summary(summary)
                    .actor(actor)
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
            attachAuditLedger(meta, organizationId, conversationId, se.getCreatedAt(), "SLA_EVENT");

            String eventTypeStr = se.getEventType() != null ? se.getEventType().name() : "EVENT";
            String actor = "SYSTEM";
            String summary = se.getReason() != null ? se.getReason() : eventTypeStr;
            String checksum = se.getAuditChecksum() != null ? se.getAuditChecksum()
                    : AuditChecksumUtils.computeSlaEventChecksum(organizationId, conversationId, se);

            events.add(TimelineEventDto.builder()
                    .id(se.getId())
                    .category("SLA_EVENT")
                    .eventType(eventTypeStr)
                    .summary(summary)
                    .actor(actor)
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
            attachAuditLedger(meta, organizationId, conversationId, csatTs, "CSAT");

            String summary = csat.getRating() != null
                    ? "CSAT Rating: " + csat.getRating() + "/5" + (csat.getFeedbackText() != null ? " - \"" + csat.getFeedbackText() + "\"" : "")
                    : "CSAT Survey Dispatched";

            String eventTypeStr = csat.getStatus() != null ? csat.getStatus().name() : "CSAT";
            String actor = "CUSTOMER";
            String checksum = AuditChecksumUtils.computeCanonicalChecksum(organizationId, conversationId, csat.getId(), "CSAT", eventTypeStr, actor, summary, csatTs);

            events.add(TimelineEventDto.builder()
                    .id(csat.getId())
                    .category("CSAT")
                    .eventType(eventTypeStr)
                    .summary(summary)
                    .actor(actor)
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
            attachAuditLedger(meta, organizationId, conversationId, audit.getCreatedAt(), "LEAD_SCORE");

            String sign = audit.getScoreDelta() > 0 ? "+" : "";
            String summary = audit.getReason() + " (Delta: " + sign + audit.getScoreDelta() + ", New: " + audit.getNewScore() + ")";
            String actor = "SYSTEM";

            String checksum = AuditChecksumUtils.computeCanonicalChecksum(organizationId, conversationId, audit.getId(), "LEAD_SCORE", "SCORE_UPDATE", actor, summary, audit.getCreatedAt());

            events.add(TimelineEventDto.builder()
                    .id(audit.getId())
                    .category("LEAD_SCORE")
                    .eventType("SCORE_UPDATE")
                    .summary(summary)
                    .actor(actor)
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

    /**
     * Verifies that a timeline event still matches the audit checksum stored with its record.
     *
     * <p>The event's checksum is recomputed from the canonical encoding of the event fields and
     * compared with the checksum carried by the event, and - for categories that persist a digest
     * column (MESSAGE, INTERNAL_NOTE, SLA_EVENT) - the persisted row is re-read and its digest
     * recomputed and compared as well.
     *
     * <p>This detects <em>uncoordinated</em> modification of the audited fields. It is not a proof
     * of immutability: the digest is unkeyed and stored in the same table as the data, so an actor
     * with write access can modify a row and re-seal it. Fields outside the audited set are not
     * covered at all.
     */
    @Override
    public boolean verifyTimelineEventIntegrity(TimelineEventDto event, UUID organizationId, UUID conversationId) {
        if (event == null || event.getAuditChecksum() == null || event.getId() == null) {
            return false;
        }

        // 1. Verify DTO canonical serialization matches auditChecksum
        String expectedChecksum = AuditChecksumUtils.computeCanonicalChecksum(
                organizationId,
                conversationId,
                event.getId(),
                event.getCategory(),
                event.getEventType(),
                event.getActor(),
                event.getSummary(),
                event.getTimestamp()
        );
        if (!AuditChecksumUtils.verifyChecksum(expectedChecksum, event.getAuditChecksum())) {
            return false;
        }

        // 2. Deep verification against underlying persistent database records:
        // Confirm that the persisted database entity has not had protected fields modified
        try {
            if ("MESSAGE".equalsIgnoreCase(event.getCategory()) && messageRepository != null) {
                Optional<Message> msgOpt = messageRepository.findById(event.getId());
                if (msgOpt.isPresent()) {
                    Message m = msgOpt.get();
                    String dbComputed = AuditChecksumUtils.computeMessageChecksum(organizationId, conversationId, m);
                    if (!AuditChecksumUtils.verifyChecksum(dbComputed, event.getAuditChecksum())) {
                        return false;
                    }
                    if (m.getAuditChecksum() != null && !AuditChecksumUtils.verifyChecksum(m.getAuditChecksum(), event.getAuditChecksum())) {
                        return false;
                    }
                }
            } else if ("INTERNAL_NOTE".equalsIgnoreCase(event.getCategory()) && internalNoteRepository != null) {
                Optional<ConversationInternalNote> noteOpt = internalNoteRepository.findById(event.getId());
                if (noteOpt.isPresent()) {
                    ConversationInternalNote n = noteOpt.get();
                    String dbComputed = AuditChecksumUtils.computeInternalNoteChecksum(organizationId, conversationId, n);
                    if (!AuditChecksumUtils.verifyChecksum(dbComputed, event.getAuditChecksum())) {
                        return false;
                    }
                    if (n.getAuditChecksum() != null && !AuditChecksumUtils.verifyChecksum(n.getAuditChecksum(), event.getAuditChecksum())) {
                        return false;
                    }
                }
            } else if ("SLA_EVENT".equalsIgnoreCase(event.getCategory()) && slaEventRepository != null) {
                Optional<ConversationSlaEvent> slaOpt = slaEventRepository.findById(event.getId());
                if (slaOpt.isPresent()) {
                    ConversationSlaEvent se = slaOpt.get();
                    String dbComputed = AuditChecksumUtils.computeSlaEventChecksum(organizationId, conversationId, se);
                    if (!AuditChecksumUtils.verifyChecksum(dbComputed, event.getAuditChecksum())) {
                        return false;
                    }
                    if (se.getAuditChecksum() != null && !AuditChecksumUtils.verifyChecksum(se.getAuditChecksum(), event.getAuditChecksum())) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Database record verification error: {}", e.getMessage());
            return false;
        }

        return true;
    }

    private void attachAuditLedger(Map<String, Object> meta, UUID organizationId, UUID conversationId, Instant ts, String category) {
        // Only records that carry a persisted digest column are actually protected by a stored
        // checksum. CSAT surveys and lead-score audits have no such column: their digest is
        // recomputed from the current row on every read, so a modification of those rows is not
        // detectable. The metadata states which of the two applies instead of claiming blanket
        // tamper-evidence.
        boolean persistedDigest = AuditChecksumUtils.CATEGORY_MESSAGE.equalsIgnoreCase(category)
                || AuditChecksumUtils.CATEGORY_INTERNAL_NOTE.equalsIgnoreCase(category)
                || AuditChecksumUtils.CATEGORY_SLA_EVENT.equalsIgnoreCase(category);
        meta.put("auditLedger", Map.of(
                "tenantId", organizationId.toString(),
                "conversationId", conversationId.toString(),
                "recordedAt", ts != null ? ts.toString() : Instant.now().toString(),
                "canonicalVersion", AuditChecksumUtils.CANONICAL_VERSION,
                "tamperEvident", persistedDigest,
                "integrityScope", "AUDITED_FIELDS",
                "protection", persistedDigest
                        ? "DETECTS_UNCOORDINATED_MODIFICATION"
                        : "RECOMPUTED_ON_READ_NO_STORED_DIGEST"
        ));
    }
}
