package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.ai.config.AiModelProperties;
import com.autoflow.modules.ai.dto.AiProviderType;
import com.autoflow.modules.ai.service.AiRouterService;
import com.autoflow.modules.crm.dto.AiCopilotDto.*;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.ArticleResponse;
import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import com.autoflow.modules.knowledge.service.KnowledgeArticleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AiCopilotService Unit Tests")
class AiCopilotServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private KnowledgeArticleService knowledgeArticleService;

    @Mock
    private AiRouterService aiRouterService;

    @Mock
    private AiModelProperties aiModelProperties;

    private AiCopilotServiceImpl copilotService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        copilotService = new AiCopilotServiceImpl(
                conversationRepository,
                messageRepository,
                knowledgeArticleService,
                aiRouterService,
                aiModelProperties
        );
        lenient().when(aiModelProperties.getDefaultProvider()).thenReturn(AiProviderType.MOCK);
        lenient().when(aiRouterService.getActiveModel(any())).thenReturn("mock-model-v1");
    }

    @Test
    @DisplayName("generateSuggestion retrieves knowledge context, invokes AI router, and records usage")
    void shouldGenerateAiSuggestionWithKnowledgeContext() {
        Contact contact = Contact.builder().fullName("Jane Doe").build();
        contact.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .contact(contact)
                .build();
        conversation.setId(testConvoId);
        conversation.setOrganizationId(testOrgId);

        Message m1 = Message.builder()
                .direction("INBOUND")
                .content("What is your refund policy?")
                .build();
        m1.setId(UUID.randomUUID());

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderBySentAtAsc(testConvoId))
                .thenReturn(List.of(m1));

        UUID articleId = UUID.randomUUID();
        ArticleResponse article = ArticleResponse.builder()
                .id(articleId)
                .title("Refund & Cancellation Policy")
                .category(KnowledgeCategory.POLICY)
                .content("30-day full refund guarantee.")
                .build();

        when(knowledgeArticleService.findRelevantArticles(eq(testOrgId), eq("What is your refund policy?"), eq(3)))
                .thenReturn(List.of(article));
        when(aiRouterService.generateReply(any(), eq("What is your refund policy?")))
                .thenReturn("Hi Jane! We offer a 30-day full refund guarantee on all orders.");

        AiSuggestionResponse response = copilotService.generateSuggestion(testOrgId, testConvoId, null);

        assertNotNull(response);
        assertEquals(testConvoId, response.getConversationId());
        assertEquals("Hi Jane! We offer a 30-day full refund guarantee on all orders.", response.getSuggestedReply());
        assertEquals(0.94, response.getConfidenceScore());
        assertFalse(response.isRequiresHumanHandoff());
        assertEquals(List.of("Refund & Cancellation Policy"), response.getSourceArticleTitles());
        verify(knowledgeArticleService).recordArticleUsage(testOrgId, articleId);
    }

    @Test
    @DisplayName("generateSuggestion detects human escalation triggers and flags handoff")
    void shouldDetectHumanEscalationKeywords() {
        Contact contact = Contact.builder().fullName("Angry Customer").build();
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.INSTAGRAM)
                .contact(contact)
                .build();
        conversation.setId(testConvoId);
        conversation.setOrganizationId(testOrgId);

        Message m1 = Message.builder()
                .direction("INBOUND")
                .content("I want to speak with a human agent right now! This is a scam.")
                .build();

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderBySentAtAsc(testConvoId))
                .thenReturn(List.of(m1));
        when(knowledgeArticleService.findRelevantArticles(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(aiRouterService.generateReply(any(), any()))
                .thenReturn("I understand your frustration. I am escalating this to our human support team immediately.");

        AiSuggestionResponse response = copilotService.generateSuggestion(testOrgId, testConvoId, null);

        assertNotNull(response);
        assertTrue(response.isRequiresHumanHandoff());
        assertNotNull(response.getHumanHandoffReason());
        assertTrue(response.getHumanHandoffReason().contains("human agent"));
        assertEquals(0.98, response.getConfidenceScore());
    }

    @Test
    @DisplayName("generateSuggestion handles empty knowledge context gracefully with general assistance")
    void shouldHandleGracefulFallbackWhenNoArticlesFound() {
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.TELEGRAM)
                .build();
        conversation.setId(testConvoId);
        conversation.setOrganizationId(testOrgId);

        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId))
                .thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderBySentAtAsc(testConvoId))
                .thenReturn(Collections.emptyList());
        when(knowledgeArticleService.findRelevantArticles(any(), any(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(aiRouterService.generateReply(any(), any()))
                .thenReturn("Hello! How can I assist you today?");

        AiSuggestionResponse response = copilotService.generateSuggestion(testOrgId, testConvoId, null);

        assertNotNull(response);
        assertEquals("Hello! How can I assist you today?", response.getSuggestedReply());
        assertEquals(0.70, response.getConfidenceScore());
        assertTrue(response.getSourceArticleTitles().isEmpty());
    }

    @Test
    @DisplayName("Cross-tenant conversation access throws ResourceNotFoundException")
    void shouldDenyCrossTenantConversationAccess() {
        when(conversationRepository.findByIdAndOrganizationId(testConvoId, testOrgId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                copilotService.generateSuggestion(testOrgId, testConvoId, null)
        );
    }
}
