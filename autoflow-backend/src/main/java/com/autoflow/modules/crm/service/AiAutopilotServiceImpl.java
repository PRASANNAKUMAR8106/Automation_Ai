package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionRequest;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionResponse;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAutopilotServiceImpl implements AiAutopilotService {

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final MessagingWindowService messagingWindowService;
    private final AiCopilotService aiCopilotService;
    private final CrmService crmService;

    @Override
    @Transactional
    public AutopilotExecutionResult processInboundAutoPilot(UUID organizationId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!conversation.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }

        Contact contact = conversation.getContact();
        ChannelType channel = conversation.getChannel();

        // 1. Re-check Contact fresh from DB (Phase 19 Persistent Consent & Suppression check)
        Contact freshContact = contactRepository.findById(contact.getId()).orElse(contact);
        if (freshContact.isSuppressed()) {
            log.info("Auto-Pilot skipped for conversation [{}]: Contact [{}] is suppressed or opted out.",
                    conversationId, freshContact.getId());
            return AutopilotExecutionResult.builder()
                    .dispatched(false)
                    .status("SKIPPED_SUPPRESSED")
                    .reason("Recipient has opted out or is suppressed")
                    .build();
        }

        if (freshContact.getExternalId() == null || freshContact.getExternalId().isBlank()) {
            return AutopilotExecutionResult.builder()
                    .dispatched(false)
                    .status("FAILED_ELIGIBILITY")
                    .reason("Missing recipient externalId")
                    .build();
        }

        // 2. Channel-Specific Messaging Eligibility & Window Compliance (Phase 19 verification)
        if (channel == ChannelType.WHATSAPP || channel == ChannelType.INSTAGRAM) {
            var window = messagingWindowService.evaluateWindow(conversation);
            if (!window.isCanSendFreeform()) {
                String reason = (channel == ChannelType.WHATSAPP)
                        ? "Outside WhatsApp 24-hour customer care session window"
                        : "Instagram 24-hour session window expired (automated AI cannot use 7-day human agent extension)";
                log.info("Auto-Pilot skipped for conversation [{}]: {}", conversationId, reason);
                return AutopilotExecutionResult.builder()
                        .dispatched(false)
                        .status("SKIPPED_WINDOW_EXPIRED")
                        .reason(reason)
                        .build();
            }
        }

        // 3. AI RAG Suggestion & Human Escalation Detection
        AiSuggestionResponse suggestion;
        try {
            suggestion = aiCopilotService.generateSuggestion(
                    organizationId,
                    conversationId,
                    AiSuggestionRequest.builder()
                            .customInstruction("Auto-Pilot Mode: Deliver concise, accurate answer strictly grounded in Knowledge Base.")
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate Auto-Pilot suggestion for conversation [{}]: {}", conversationId, e.getMessage());
            return AutopilotExecutionResult.builder()
                    .dispatched(false)
                    .status("SKIPPED_AI_ERROR")
                    .reason("AI generation error: " + e.getMessage())
                    .build();
        }

        // 4. Escalation Protection: If customer asks for human or expresses frustration, halt Auto-Pilot
        if (suggestion.isRequiresHumanHandoff()) {
            log.info("Auto-Pilot halted for conversation [{}]: Escalation detected [{}]. Preserved for human agent.",
                    conversationId, suggestion.getHumanHandoffReason());
            return AutopilotExecutionResult.builder()
                    .dispatched(false)
                    .status("SKIPPED_HUMAN_HANDOFF")
                    .reason(suggestion.getHumanHandoffReason())
                    .build();
        }

        // 5. Dispatch Auto-Pilot Reply through CRM and Social Channel Provider
        try {
            Message reply = crmService.sendAgentReply(
                    organizationId,
                    conversationId,
                    suggestion.getSuggestedReply(),
                    null,
                    false
            );

            log.info("Auto-Pilot successfully dispatched automated reply for conversation [{}] on channel [{}]",
                    conversationId, channel);

            return AutopilotExecutionResult.builder()
                    .dispatched(true)
                    .status("DISPATCHED")
                    .messageContent(suggestion.getSuggestedReply())
                    .externalMessageId(reply.getExternalMessageId())
                    .reason("Auto-Pilot response successfully dispatched")
                    .build();
        } catch (Exception e) {
            log.error("Failed to dispatch Auto-Pilot message for conversation [{}]: {}", conversationId, e.getMessage());
            return AutopilotExecutionResult.builder()
                    .dispatched(false)
                    .status("FAILED_DISPATCH")
                    .reason("Provider dispatch error: " + e.getMessage())
                    .build();
        }
    }
}
