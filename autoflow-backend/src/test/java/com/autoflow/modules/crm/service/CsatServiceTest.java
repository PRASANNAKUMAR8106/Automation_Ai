package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.CsatSurveyRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CsatService Unit Tests")
class CsatServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private CsatSurveyRepository csatSurveyRepository;

    @Mock
    private MessagingWindowService messagingWindowService;

    @Mock
    private MessageRepository messageRepository;

    private CsatServiceImpl csatService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();
    private final UUID testContactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        csatService = new CsatServiceImpl(
                conversationRepository,
                contactRepository,
                csatSurveyRepository,
                messagingWindowService,
                messageRepository
        );
    }

    private Conversation createConversation(Contact contact) {
        Conversation convo = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .contact(contact)
                .resolved(true)
                .build();
        convo.setId(testConvoId);
        convo.setOrganizationId(testOrgId);
        return convo;
    }

    private Contact createContact(boolean suppressed) {
        Contact contact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId("wa_987")
                .fullName("Jordan Lee")
                .tags(suppressed ? List.of("opt_out") : List.of())
                .build();
        contact.setId(testContactId);
        contact.setOrganizationId(testOrgId);
        return contact;
    }

    @Test
    @DisplayName("Should successfully dispatch CSAT survey when recipient is eligible and window is active")
    void shouldDispatchSurveyWhenEligibleAndWindowActive() {
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.empty());
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        CrmDto.MessagingWindowResponse activeWindow = CrmDto.MessagingWindowResponse.builder()
                .canSendFreeform(true)
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(activeWindow);

        when(csatSurveyRepository.save(any(CsatSurvey.class))).thenAnswer(i -> {
            CsatSurvey s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CsatSurveyResponse response = csatService.triggerPostResolutionSurvey(testOrgId, testConvoId);

        assertNotNull(response);
        assertEquals("DISPATCHED", response.getStatus());
        verify(messageRepository).save(any(Message.class));
        verify(csatSurveyRepository).save(any(CsatSurvey.class));
    }

    @Test
    @DisplayName("Idempotency: Re-dispatching survey for already surveyed conversation returns existing survey without duplicate message")
    void shouldBeIdempotentOnConcurrentOrRepeatDispatch() {
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);

        CsatSurvey existingSurvey = CsatSurvey.builder()
                .conversation(conversation)
                .contact(contact)
                .status(CsatStatus.DISPATCHED)
                .build();
        existingSurvey.setId(UUID.randomUUID());
        existingSurvey.setOrganizationId(testOrgId);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.of(existingSurvey));

        CsatSurveyResponse response = csatService.triggerPostResolutionSurvey(testOrgId, testConvoId);

        assertNotNull(response);
        assertEquals(existingSurvey.getId(), response.getId());
        verifyNoInteractions(messageRepository);
        verify(csatSurveyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Phase 19 Compliance: Should skip CSAT survey when contact is suppressed or opted out")
    void shouldSkipSurveyWhenContactIsSuppressed() {
        Contact contact = createContact(true); // suppressed via "opt_out" tag
        Conversation conversation = createConversation(contact);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.empty());
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        when(csatSurveyRepository.save(any(CsatSurvey.class))).thenAnswer(i -> {
            CsatSurvey s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CsatSurveyResponse response = csatService.triggerPostResolutionSurvey(testOrgId, testConvoId);

        assertNotNull(response);
        assertEquals("SKIPPED_SUPPRESSED", response.getStatus());
        verifyNoInteractions(messagingWindowService);
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("Phase 18 Compliance: Should skip CSAT survey when 24-hour care window is expired and no template is configured")
    void shouldSkipSurveyWhenWindowExpiredAndNoTemplate() {
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.empty());
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        CrmDto.MessagingWindowResponse expiredWindow = CrmDto.MessagingWindowResponse.builder()
                .canSendFreeform(false)
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(expiredWindow);

        when(csatSurveyRepository.save(any(CsatSurvey.class))).thenAnswer(i -> {
            CsatSurvey s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CsatSurveyResponse response = csatService.triggerPostResolutionSurvey(testOrgId, testConvoId);

        assertNotNull(response);
        assertEquals("SKIPPED_WINDOW_EXPIRED", response.getStatus());
        verifyNoInteractions(messageRepository);
    }

    @Test
    @DisplayName("Phase 21 WhatsApp Template Fallback: Dispatches approved template when window is closed but template is permitted")
    void shouldDispatchApprovedTemplateWhenWindowClosedAndTemplateEnabled() {
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);

        ConversationSlaPolicy policy = ConversationSlaPolicy.builder()
                .whatsappTemplateEnabled(true)
                .build();
        conversation.setSlaPolicy(policy);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.empty());
        when(contactRepository.findById(testContactId)).thenReturn(Optional.of(contact));

        CrmDto.MessagingWindowResponse expiredWindow = CrmDto.MessagingWindowResponse.builder()
                .canSendFreeform(false)
                .build();
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(expiredWindow);

        when(csatSurveyRepository.save(any(CsatSurvey.class))).thenAnswer(i -> {
            CsatSurvey s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CsatSurveyResponse response = csatService.triggerPostResolutionSurvey(testOrgId, testConvoId);

        assertNotNull(response);
        assertEquals("DISPATCHED", response.getStatus());
        verify(messageRepository).save(argThat(m -> "TEMPLATE".equals(m.getMessageType())));
    }

    @Test
    @DisplayName("Should record feedback rating, enforce 1-5 scale, and escalate priority to HIGH if rating is low (<= 2)")
    void shouldRecordFeedbackAndEscalateOnLowRating() {
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);
        conversation.setPriority(ConversationPriority.NORMAL);

        CsatSurvey survey = CsatSurvey.builder()
                .conversation(conversation)
                .contact(contact)
                .status(CsatStatus.DISPATCHED)
                .build();
        survey.setId(UUID.randomUUID());
        survey.setOrganizationId(testOrgId);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.of(survey));
        when(csatSurveyRepository.save(any(CsatSurvey.class))).thenAnswer(i -> i.getArgument(0));

        SubmitCsatRequest request = SubmitCsatRequest.builder()
                .rating(1)
                .feedbackText("Support agent was unhelpful and did not solve my issue.")
                .build();

        CsatSurveyResponse response = csatService.submitFeedback(testOrgId, testConvoId, request);

        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals(1, response.getRating());
        assertEquals(ConversationPriority.HIGH, conversation.getPriority(), "Low rating must escalate conversation priority to HIGH");
        verify(conversationRepository).save(conversation);
    }

    @Test
    @DisplayName("Should reject feedback submission if rating is out of bounds or survey is already completed")
    void shouldRejectInvalidRatingOrDuplicateSubmission() {
        // 1. Out of bounds rating
        SubmitCsatRequest invalidRating = SubmitCsatRequest.builder().rating(6).build();
        assertThrows(IllegalArgumentException.class, () ->
                csatService.submitFeedback(testOrgId, testConvoId, invalidRating));

        // 2. Already completed survey
        Contact contact = createContact(false);
        Conversation conversation = createConversation(contact);
        CsatSurvey completedSurvey = CsatSurvey.builder()
                .conversation(conversation)
                .contact(contact)
                .status(CsatStatus.COMPLETED)
                .build();
        completedSurvey.setOrganizationId(testOrgId);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId)).thenReturn(Optional.of(conversation));
        when(csatSurveyRepository.findByOrganizationIdAndConversationId(testOrgId, testConvoId)).thenReturn(Optional.of(completedSurvey));

        SubmitCsatRequest validRequest = SubmitCsatRequest.builder().rating(5).build();
        assertThrows(IllegalStateException.class, () ->
                csatService.submitFeedback(testOrgId, testConvoId, validRequest));
    }
}
