package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.TimelineEventDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.*;
import com.autoflow.modules.crm.service.impl.ConversationTimelineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationTimelineService Unit Tests")
class ConversationTimelineServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CrmService crmService;

    @Mock
    private ConversationInternalNoteRepository internalNoteRepository;

    @Mock
    private ConversationSlaEventRepository slaEventRepository;

    @Mock
    private CsatSurveyRepository csatSurveyRepository;

    @Mock
    private ContactLeadScoreAuditRepository leadScoreAuditRepository;

    private ConversationTimelineServiceImpl timelineService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        timelineService = new ConversationTimelineServiceImpl(
                conversationRepository,
                crmService,
                internalNoteRepository,
                slaEventRepository,
                csatSurveyRepository,
                leadScoreAuditRepository
        );
    }

    @Test
    @DisplayName("Should aggregate messages, notes, SLA events, CSAT, and lead scores in chronological order")
    void shouldAggregateChronologicalTimeline() {
        Conversation conversation = Conversation.builder().build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));

        Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant t1 = t0.plus(5, ChronoUnit.MINUTES);
        Instant t2 = t0.plus(15, ChronoUnit.MINUTES);
        Instant t3 = t0.plus(25, ChronoUnit.MINUTES);
        Instant t4 = t0.plus(35, ChronoUnit.MINUTES);

        // 1. Message at t0
        Message msg = Message.builder()
                .direction("INBOUND")
                .senderType("CONTACT")
                .content("I need help with my invoice.")
                .sentAt(t0)
                .build();
        msg.setId(UUID.randomUUID());
        when(crmService.getMessages(orgId, convoId)).thenReturn(List.of(msg));

        // 2. SLA Warning at t1
        ConversationSlaEvent slaEvent = ConversationSlaEvent.builder()
                .eventType(ConversationSlaEventType.WARNING)
                .reason("Approaching First Response Deadline")
                .build();
        slaEvent.setId(UUID.randomUUID());
        slaEvent.setCreatedAt(t1);
        when(slaEventRepository.findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(orgId, convoId))
                .thenReturn(List.of(slaEvent));

        // 3. Internal Note at t2
        ConversationInternalNote note = ConversationInternalNote.builder()
                .noteType("INTERNAL_NOTE")
                .content("Escalated to billing finance team.")
                .authorEmail("agent@autoflow.ai")
                .build();
        note.setId(UUID.randomUUID());
        note.setCreatedAt(t2);
        when(internalNoteRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(orgId, convoId))
                .thenReturn(List.of(note));

        // 4. Lead Score update at t3
        ContactLeadScoreAudit audit = ContactLeadScoreAudit.builder()
                .reason("Negative Sentiment Detected")
                .previousScore(50)
                .newScore(40)
                .scoreDelta(-10)
                .build();
        audit.setId(UUID.randomUUID());
        audit.setCreatedAt(t3);
        when(leadScoreAuditRepository.findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(orgId, convoId))
                .thenReturn(List.of(audit));

        // 5. CSAT Survey at t4
        CsatSurvey csat = CsatSurvey.builder()
                .status(CsatStatus.COMPLETED)
                .rating(5)
                .feedbackText("Great quick assistance!")
                .dispatchedAt(t3.plus(5, ChronoUnit.MINUTES))
                .respondedAt(t4)
                .build();
        csat.setId(UUID.randomUUID());
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(orgId, convoId))
                .thenReturn(Optional.of(csat));

        List<TimelineEventDto> timeline = timelineService.getConversationTimeline(orgId, convoId);

        assertThat(timeline).hasSize(5);
        assertThat(timeline.get(0).getCategory()).isEqualTo("MESSAGE");
        assertThat(timeline.get(1).getCategory()).isEqualTo("SLA_EVENT");
        assertThat(timeline.get(2).getCategory()).isEqualTo("INTERNAL_NOTE");
        assertThat(timeline.get(3).getCategory()).isEqualTo("LEAD_SCORE");
        assertThat(timeline.get(4).getCategory()).isEqualTo("CSAT");

        // Verify sorted order
        for (int i = 0; i < timeline.size() - 1; i++) {
            assertThat(timeline.get(i).getTimestamp()).isBeforeOrEqualTo(timeline.get(i + 1).getTimestamp());
        }
    }
}
