package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionResponse;
import com.autoflow.modules.crm.dto.CrmDto.MessagingWindowResponse;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.AiAutopilotService.AutopilotExecutionResult;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiAutopilotService Unit Tests")
class AiAutopilotServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private MessagingWindowService messagingWindowService;

    @Mock
    private AiCopilotService aiCopilotService;

    @Mock
    private CrmService crmService;

    private AiAutopilotServiceImpl autopilotService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();
    private final UUID testContactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        autopilotService = new AiAutopilotServiceImpl(
                conversationRepository,
                contactRepository,
                messagingWindowService,
                aiCopilotService,
                crmService
        );
    }

    private Conversation createTestConversation(ChannelType channel, Contact contact) {
        Conversation convo = Conversation.builder()
                .channel(channel)
                .contact(contact)
                .build();
        convo.setId(testConvoId);
        convo.setOrganizationId(testOrgId);
        return convo;
    }

    private Contact createTestContact(boolean suppressed, String externalId) {
        Contact contact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId(externalId)
                .fullName("Alex Smith")
                .username("alex_smith")
                .tags(suppressed ? List.of("suppressed") : List.of())
                .build();
        contact.setId(testContactId);
        contact.setOrganizationId(testOrgId);
        return contact;
    }

    @Test
    @DisplayName("Auto-Pilot skips execution when recipient contact is suppressed or opted out (Phase 19 Consent Re-check)")
    void shouldSkipWhenContactIsSuppressed() {
        Contact contact = createTestContact(true, "wa_12345");
        Conversation conversation = createTestConversation(ChannelType.WHATSAPP, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertFalse(result.isDispatched());
        assertEquals("SKIPPED_SUPPRESSED", result.getStatus());
        assertTrue(result.getReason().contains("opted out or is suppressed"));

        verifyNoInteractions(messagingWindowService);
        verifyNoInteractions(aiCopilotService);
        verifyNoInteractions(crmService);
    }

    @Test
    @DisplayName("Auto-Pilot fails eligibility when recipient external ID is missing")
    void shouldFailEligibilityWhenRecipientExternalIdIsMissing() {
        Contact contact = createTestContact(false, null);
        Conversation conversation = createTestConversation(ChannelType.WHATSAPP, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertFalse(result.isDispatched());
        assertEquals("FAILED_ELIGIBILITY", result.getStatus());
        assertTrue(result.getReason().contains("Missing recipient externalId"));

        verifyNoInteractions(aiCopilotService);
    }

    @Test
    @DisplayName("Auto-Pilot skips execution when WhatsApp 24-hour customer care session window is expired")
    void shouldSkipWhenWhatsAppSessionWindowIsExpired() {
        Contact contact = createTestContact(false, "wa_12345");
        Conversation conversation = createTestConversation(ChannelType.WHATSAPP, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        MessagingWindowResponse expiredWindow = MessagingWindowResponse.builder()
                .channel(ChannelType.WHATSAPP)
                .canSendFreeform(false)
                .canSendHumanAgent(false)
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(expiredWindow);

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertFalse(result.isDispatched());
        assertEquals("SKIPPED_WINDOW_EXPIRED", result.getStatus());
        assertTrue(result.getReason().contains("Outside WhatsApp 24-hour"));

        verifyNoInteractions(aiCopilotService);
        verifyNoInteractions(crmService);
    }

    @Test
    @DisplayName("Auto-Pilot skips execution when Instagram 24-hour session window is expired (automated AI cannot use 7-day human agent extension)")
    void shouldSkipWhenInstagramSessionWindowIsExpired() {
        Contact contact = createTestContact(false, "ig_98765");
        Conversation conversation = createTestConversation(ChannelType.INSTAGRAM, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        MessagingWindowResponse igExpiredWindow = MessagingWindowResponse.builder()
                .channel(ChannelType.INSTAGRAM)
                .canSendFreeform(false)
                .canSendHumanAgent(true) // Human agent tag available for humans, but NOT automated AI
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(igExpiredWindow);

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertFalse(result.isDispatched());
        assertEquals("SKIPPED_WINDOW_EXPIRED", result.getStatus());
        assertTrue(result.getReason().contains("Instagram 24-hour session window expired"));

        verifyNoInteractions(aiCopilotService);
        verifyNoInteractions(crmService);
    }

    @Test
    @DisplayName("Auto-Pilot halts and preserves handoff when AI suggestion indicates human escalation required")
    void shouldHaltWhenAiSuggestionRequiresHumanHandoff() {
        Contact contact = createTestContact(false, "tg_555");
        Conversation conversation = createTestConversation(ChannelType.TELEGRAM, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        AiSuggestionResponse suggestion = AiSuggestionResponse.builder()
                .suggestedReply("Connecting you with our support team.")
                .confidenceScore(0.3)
                .requiresHumanHandoff(true)
                .humanHandoffReason("User requested human agent escalation")
                .build();
        when(aiCopilotService.generateSuggestion(eq(testOrgId), eq(testConvoId), any())).thenReturn(suggestion);

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertFalse(result.isDispatched());
        assertEquals("SKIPPED_HUMAN_HANDOFF", result.getStatus());
        assertEquals("User requested human agent escalation", result.getReason());

        verifyNoInteractions(crmService);
    }

    @Test
    @DisplayName("Auto-Pilot successfully dispatches compliant reply when all eligibility, consent, and safety checks pass")
    void shouldDispatchWhenAllEligibilityAndAiChecksPass() {
        Contact contact = createTestContact(false, "wa_99999");
        Conversation conversation = createTestConversation(ChannelType.WHATSAPP, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        MessagingWindowResponse openWindow = MessagingWindowResponse.builder()
                .channel(ChannelType.WHATSAPP)
                .canSendFreeform(true)
                .windowExpiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(openWindow);

        AiSuggestionResponse suggestion = AiSuggestionResponse.builder()
                .suggestedReply("Yes, we offer 30-day hassle-free refunds on all subscriptions.")
                .confidenceScore(0.95)
                .requiresHumanHandoff(false)
                .build();
        when(aiCopilotService.generateSuggestion(eq(testOrgId), eq(testConvoId), any())).thenReturn(suggestion);

        Message sentMessage = Message.builder()
                .content(suggestion.getSuggestedReply())
                .externalMessageId("ext_msg_wa_abc123")
                .senderType("AI")
                .build();
        when(crmService.sendAgentReply(eq(testOrgId), eq(testConvoId), eq(suggestion.getSuggestedReply()), isNull(), eq(false)))
                .thenReturn(sentMessage);

        AutopilotExecutionResult result = autopilotService.processInboundAutoPilot(testOrgId, testConvoId);

        assertNotNull(result);
        assertTrue(result.isDispatched());
        assertEquals("DISPATCHED", result.getStatus());
        assertEquals(suggestion.getSuggestedReply(), result.getMessageContent());
        assertEquals("ext_msg_wa_abc123", result.getExternalMessageId());

        verify(crmService).sendAgentReply(eq(testOrgId), eq(testConvoId), eq(suggestion.getSuggestedReply()), isNull(), eq(false));
    }

    @Test
    @DisplayName("Cross-tenant security: throws ResourceNotFoundException if conversation does not belong to organization")
    void shouldEnforceCrossTenantIsolation() {
        UUID wrongOrgId = UUID.randomUUID();
        Contact contact = createTestContact(false, "wa_12345");
        Conversation conversation = createTestConversation(ChannelType.WHATSAPP, contact);

        when(conversationRepository.findById(testConvoId)).thenReturn(Optional.of(conversation));

        assertThrows(ResourceNotFoundException.class, () ->
                autopilotService.processInboundAutoPilot(wrongOrgId, testConvoId));

        verifyNoInteractions(contactRepository);
        verifyNoInteractions(messagingWindowService);
        verifyNoInteractions(crmService);
    }
}
