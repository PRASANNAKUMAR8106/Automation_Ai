package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.ConversationSlaPolicyRepository;
import com.autoflow.modules.crm.repository.CsatSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SlaMonitoringService Unit Tests")
class SlaMonitoringServiceTest {

    @Mock
    private ConversationSlaPolicyRepository policyRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CsatSurveyRepository csatSurveyRepository;

    private SlaMonitoringServiceImpl slaService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        slaService = new SlaMonitoringServiceImpl(policyRepository, conversationRepository, csatSurveyRepository);
    }

    @Test
    @DisplayName("Should apply matched SLA policy and calculate response and resolution deadlines")
    void shouldApplyMatchedSlaPolicy() {
        ConversationSlaPolicy customPolicy = ConversationSlaPolicy.builder()
                .name("VIP WhatsApp SLA")
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.URGENT)
                .firstResponseTimeSeconds(180) // 3 minutes
                .resolutionTimeSeconds(1800)   // 30 minutes
                .active(true)
                .build();

        when(policyRepository.findFirstByOrganizationIdAndChannelAndPriorityAndActiveTrue(
                testOrgId, ChannelType.WHATSAPP, ConversationPriority.URGENT))
                .thenReturn(Optional.of(customPolicy));

        Conversation convo = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.URGENT)
                .build();
        convo.setId(UUID.randomUUID());
        convo.setOrganizationId(testOrgId);

        slaService.applySlaPolicy(convo);

        assertNotNull(convo.getSlaFirstResponseDueAt());
        assertNotNull(convo.getSlaResolutionDueAt());
        assertEquals(customPolicy, convo.getSlaPolicy());
    }

    @Test
    @DisplayName("Should detect SLA First Response breach when agent reply occurs after deadline")
    void shouldDetectFirstResponseBreachWhenLate() {
        Conversation convo = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.NORMAL)
                .slaFirstResponseDueAt(Instant.now().minusSeconds(300)) // 5 minutes ago (late)
                .build();
        convo.setId(UUID.randomUUID());
        convo.setOrganizationId(testOrgId);

        slaService.recordFirstAgentReply(convo);

        assertNotNull(convo.getFirstAgentReplyAt());
        assertTrue(convo.isSlaFirstResponseBreached(), "Late reply must set slaFirstResponseBreached to true");
    }

    @Test
    @DisplayName("Should mark first response compliant when agent reply occurs before deadline")
    void shouldMarkFirstResponseCompliantWhenOnTime() {
        Conversation convo = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.NORMAL)
                .slaFirstResponseDueAt(Instant.now().plusSeconds(600)) // 10 minutes in future (on time)
                .build();
        convo.setId(UUID.randomUUID());
        convo.setOrganizationId(testOrgId);

        slaService.recordFirstAgentReply(convo);

        assertNotNull(convo.getFirstAgentReplyAt());
        assertFalse(convo.isSlaFirstResponseBreached(), "On-time reply must not breach SLA");
    }

    @Test
    @DisplayName("Background scanner flags overdue conversations as breached")
    void shouldScanAndFlagOverdueBreaches() {
        Conversation overdueConvo = Conversation.builder()
                .priority(ConversationPriority.HIGH)
                .slaFirstResponseDueAt(Instant.now().minusSeconds(60))
                .slaFirstResponseBreached(false)
                .build();
        overdueConvo.setId(UUID.randomUUID());
        overdueConvo.setOrganizationId(testOrgId);

        when(conversationRepository.findPendingFirstResponseBreaches(any(Instant.class)))
                .thenReturn(List.of(overdueConvo));
        when(conversationRepository.findPendingResolutionBreaches(any(Instant.class)))
                .thenReturn(List.of());

        slaService.scanAndProcessBreaches();

        assertTrue(overdueConvo.isSlaFirstResponseBreached());
        verify(conversationRepository).save(overdueConvo);
    }

    @Test
    @DisplayName("Performance analytics computes compliance rates, average times, and AI deflection rate")
    void shouldCalculatePerformanceAnalytics() {
        Instant t0 = Instant.now().minusSeconds(3600); // 1 hour ago
        Instant t1 = t0.plusSeconds(300);              // replied in 5 min
        Instant t2 = t0.plusSeconds(1800);             // resolved in 30 min

        // Conversation 1: human replied in 5 min, resolved in 30 min, compliant
        Conversation c1 = Conversation.builder()
                .resolved(true)
                .firstAgentReplyAt(t1)
                .resolvedAt(t2)
                .slaFirstResponseBreached(false)
                .slaResolutionBreached(false)
                .build();
        c1.setCreatedAt(t0);

        // Conversation 2: deflected by AI (no human reply, resolved)
        Conversation c2 = Conversation.builder()
                .resolved(true)
                .firstAgentReplyAt(null)
                .resolvedAt(t0.plusSeconds(60))
                .slaFirstResponseBreached(false)
                .slaResolutionBreached(false)
                .build();
        c2.setCreatedAt(t0);

        when(conversationRepository.findAllByOrganizationId(testOrgId)).thenReturn(List.of(c1, c2));
        when(csatSurveyRepository.getAverageRatingByOrganizationId(testOrgId)).thenReturn(4.8);
        when(csatSurveyRepository.countCompletedSurveysByOrganizationId(testOrgId)).thenReturn(10L);

        PerformanceAnalyticsResponse analytics = slaService.getPerformanceAnalytics(testOrgId);

        assertNotNull(analytics);
        assertEquals(2L, analytics.getTotalConversations());
        assertEquals(2L, analytics.getResolvedConversations());
        assertEquals(0L, analytics.getActiveConversations());
        assertEquals(4.8, analytics.getAverageCsatRating());
        assertEquals(10L, analytics.getTotalCsatResponses());
        assertEquals(50.0, analytics.getAiAutoPilotDeflectionRate(), "1 of 2 resolved conversations was deflected by AI = 50%");
    }
}
