package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SentimentAnalysisService Unit Tests")
class SentimentAnalysisServiceTest {

    @Mock
    private ContactRepository contactRepository;

    private SentimentAnalysisServiceImpl sentimentService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sentimentService = new SentimentAnalysisServiceImpl(contactRepository);
    }

    @Test
    @DisplayName("Should classify positive sentiment and increment lead score")
    void shouldClassifyPositiveSentiment() {
        Contact contact = Contact.builder()
                .leadScore(10)
                .leadStatus(LeadStatus.NEW)
                .build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation conversation = Conversation.builder()
                .priority(ConversationPriority.NORMAL)
                .contact(contact)
                .build();

        String positiveMessage = "Thank you so much! Your product is fantastic and super helpful.";

        sentimentService.processInboundIntelligence(conversation, contact, positiveMessage);

        assertEquals(ConversationSentiment.POSITIVE, conversation.getSentiment());
        assertEquals(ConversationPriority.NORMAL, conversation.getPriority());
        assertEquals(20, contact.getLeadScore());
        verify(contactRepository).save(contact);
    }

    @Test
    @DisplayName("Should detect churn risk, escalate priority to URGENT, and deduct lead score")
    void shouldClassifyChurnRiskAndEscalateToUrgent() {
        Contact contact = Contact.builder()
                .leadScore(40)
                .leadStatus(LeadStatus.NEW)
                .build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation conversation = Conversation.builder()
                .priority(ConversationPriority.NORMAL)
                .contact(contact)
                .build();

        String churnMessage = "Cancel subscription immediately! This is unacceptable and I will contact my lawyer.";

        sentimentService.processInboundIntelligence(conversation, contact, churnMessage);

        assertEquals(ConversationSentiment.CHURN_RISK, conversation.getSentiment());
        assertEquals(ConversationPriority.URGENT, conversation.getPriority());
        assertEquals(20, contact.getLeadScore()); // 40 - 20 = 20
        verify(contactRepository).save(contact);
    }

    @Test
    @DisplayName("Should classify negative sentiment and escalate priority to HIGH")
    void shouldClassifyNegativeSentiment() {
        Contact contact = Contact.builder()
                .leadScore(25)
                .leadStatus(LeadStatus.NEW)
                .build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation conversation = Conversation.builder()
                .priority(ConversationPriority.NORMAL)
                .contact(contact)
                .build();

        String negMessage = "The webhook integration is broken and failed with an error.";

        sentimentService.processInboundIntelligence(conversation, contact, negMessage);

        assertEquals(ConversationSentiment.NEGATIVE, conversation.getSentiment());
        assertEquals(ConversationPriority.HIGH, conversation.getPriority());
        assertEquals(20, contact.getLeadScore()); // 25 - 5 = 20
        verify(contactRepository).save(contact);
    }

    @Test
    @DisplayName("Should detect high purchase intent and automatically advance lead to QUALIFIED status")
    void shouldDetectHighIntentAndAutoQualifyLead() {
        Contact contact = Contact.builder()
                .leadScore(40)
                .leadStatus(LeadStatus.NEW)
                .build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation conversation = Conversation.builder()
                .priority(ConversationPriority.NORMAL)
                .contact(contact)
                .build();

        String highIntentMessage = "We love this tool and want to see the pricing and upgrade to the enterprise plan!";

        sentimentService.processInboundIntelligence(conversation, contact, highIntentMessage);

        assertEquals(ConversationSentiment.POSITIVE, conversation.getSentiment());
        assertEquals(ConversationPriority.HIGH, conversation.getPriority());
        // 40 + 10 (positive) + 15 (intent) = 65 >= 50
        assertEquals(65, contact.getLeadScore());
        assertEquals(LeadStatus.QUALIFIED, contact.getLeadStatus());
        verify(contactRepository).save(contact);
    }
}
