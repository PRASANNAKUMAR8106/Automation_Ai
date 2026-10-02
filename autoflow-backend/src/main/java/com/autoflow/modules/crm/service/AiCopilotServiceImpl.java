package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.ai.config.AiModelProperties;
import com.autoflow.modules.ai.service.AiRouterService;
import com.autoflow.modules.crm.dto.AiCopilotDto.*;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import com.autoflow.modules.knowledge.dto.KnowledgeDto.ArticleResponse;
import com.autoflow.modules.knowledge.service.KnowledgeArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotServiceImpl implements AiCopilotService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeArticleService knowledgeArticleService;
    private final AiRouterService aiRouterService;
    private final AiModelProperties aiModelProperties;

    private static final Pattern ESCALATION_PATTERN = Pattern.compile(
            "\\b(human|agent|manager|representative|person|dispute|scam|fraud|lawyer|sue|cancel my (account|order|subscription)|chargeback)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    @Transactional
    public AiSuggestionResponse generateSuggestion(UUID organizationId, UUID conversationId, AiSuggestionRequest request) {
        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        List<Message> history = messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);

        String channelName = conversation.getChannel() != null ? conversation.getChannel().name() : "SOCIAL";
        String contactName = (conversation.getContact() != null && conversation.getContact().getFullName() != null)
                ? conversation.getContact().getFullName()
                : "the customer";

        // Find recent customer messages
        List<Message> customerMessages = history.stream()
                .filter(m -> "INBOUND".equalsIgnoreCase(m.getDirection()))
                .toList();

        String latestCustomerText = !customerMessages.isEmpty()
                ? customerMessages.get(customerMessages.size() - 1).getContent()
                : (history.isEmpty() ? "Hello" : history.get(history.size() - 1).getContent());

        if (latestCustomerText == null) {
            latestCustomerText = "Hello";
        }

        // 1. Evaluate human escalation
        boolean requiresHuman = false;
        String escalationReason = null;
        if (ESCALATION_PATTERN.matcher(latestCustomerText).find()) {
            requiresHuman = true;
            escalationReason = "Customer requested human agent assistance or reported dispute.";
            log.info("Human escalation triggered for conversation [{}] by text: [{}]", conversationId, latestCustomerText);
        }

        // 2. Query knowledge articles
        List<ArticleResponse> relevantArticles = knowledgeArticleService.findRelevantArticles(
                organizationId,
                latestCustomerText,
                3
        );

        List<String> articleTitles = relevantArticles.stream()
                .map(ArticleResponse::getTitle)
                .collect(Collectors.toList());

        // Record usage for retrieved articles
        for (ArticleResponse article : relevantArticles) {
            knowledgeArticleService.recordArticleUsage(organizationId, article.getId());
        }

        // 3. Assemble Prompt
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an expert AI Co-Pilot assistant supporting our support team on ").append(channelName).append(".\n");
        if (request != null && request.getPersonaOverride() != null && !request.getPersonaOverride().isBlank()) {
            promptBuilder.append("Brand Persona: ").append(request.getPersonaOverride().trim()).append("\n");
        } else {
            promptBuilder.append("Brand Persona: Friendly, empathetic, accurate, and concise. Address ").append(contactName).append(" naturally.\n");
        }
        if (request != null && request.getCustomInstruction() != null && !request.getCustomInstruction().isBlank()) {
            promptBuilder.append("Operator Instructions: ").append(request.getCustomInstruction().trim()).append("\n");
        }

        promptBuilder.append("\n=== KNOWLEDGE BASE CONTEXT ===\n");
        if (relevantArticles.isEmpty()) {
            promptBuilder.append("(No specific knowledge base articles found. Respond politely with general assistance and offer to connect with a team member if needed.)\n");
        } else {
            for (ArticleResponse a : relevantArticles) {
                promptBuilder.append("--- Article: ").append(a.getTitle()).append(" [Category: ").append(a.getCategory()).append("] ---\n");
                promptBuilder.append(a.getContent()).append("\n\n");
            }
        }

        promptBuilder.append("=== RECENT CONVERSATION HISTORY ===\n");
        int historyStart = Math.max(0, history.size() - 6);
        for (int i = historyStart; i < history.size(); i++) {
            Message m = history.get(i);
            String role = "INBOUND".equalsIgnoreCase(m.getDirection()) ? "Customer" : "Agent";
            promptBuilder.append("[").append(role).append("]: ").append(m.getContent()).append("\n");
        }

        promptBuilder.append("\nGuidelines:\n");
        promptBuilder.append("1. Answer the customer directly and concisely without fluff.\n");
        promptBuilder.append("2. Strictly adhere to the Knowledge Base Context. Never hallucinate fake promotions or policies.\n");
        promptBuilder.append("3. Format appropriately for ").append(channelName).append(" (no markdown headers, keep it under 3-4 sentences).\n");
        if (requiresHuman) {
            promptBuilder.append("4. Acknowledge the request to speak with a human agent and reassure them that an agent is being notified.\n");
        }

        // 4. Generate AI suggestion
        String suggestedReply;
        try {
            suggestedReply = aiRouterService.generateReply(promptBuilder.toString(), latestCustomerText);
        } catch (Exception e) {
            log.error("Failed to generate AI suggestion via router: {}", e.getMessage(), e);
            suggestedReply = "Hi " + contactName + ", thank you for reaching out! Let me connect you with a team member who can help you right away.";
            requiresHuman = true;
            escalationReason = "Automated AI generation unavailable. Human agent review required.";
        }

        double confidence = relevantArticles.isEmpty() ? 0.70 : 0.94;
        if (requiresHuman) {
            confidence = 0.98;
        }

        String activeModel = aiRouterService.getActiveModel(aiModelProperties.getDefaultProvider());

        return AiSuggestionResponse.builder()
                .conversationId(conversationId)
                .suggestedReply(suggestedReply)
                .confidenceScore(confidence)
                .requiresHumanHandoff(requiresHuman)
                .humanHandoffReason(escalationReason)
                .sourceArticleTitles(articleTitles)
                .activeModel(activeModel)
                .build();
    }
}
