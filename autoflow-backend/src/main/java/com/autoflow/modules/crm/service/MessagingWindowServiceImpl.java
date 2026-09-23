package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingWindowServiceImpl implements MessagingWindowService {

    private static final long WINDOW_24H_SECONDS = 24 * 3600L; // 86,400 seconds
    private static final long WINDOW_7D_SECONDS = 7 * 24 * 3600L; // 604,800 seconds

    private final ConversationRepository conversationRepository;

    @Override
    @Transactional(readOnly = true)
    public CrmDto.MessagingWindowResponse getWindowStatus(UUID organizationId, UUID conversationId) {
        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        return evaluateWindow(conversation);
    }

    @Override
    public CrmDto.MessagingWindowResponse evaluateWindow(Conversation conversation) {
        ChannelType channel = conversation.getChannel();
        Instant now = Instant.now();

        // 1. Telegram has no 24-hour window restriction
        if (channel == ChannelType.TELEGRAM) {
            return CrmDto.MessagingWindowResponse.builder()
                    .conversationId(conversation.getId())
                    .channel(channel)
                    .windowStatus("UNRESTRICTED")
                    .remainingSeconds(null)
                    .lastCustomerMessageAt(conversation.getLastCustomerMessageAt())
                    .windowExpiresAt(null)
                    .canSendFreeform(true)
                    .canSendHumanAgent(true)
                    .policyDescription("Telegram allows direct messaging to bot subscribers with no 24-hour window limitation.")
                    .build();
        }

        // 2. Meta Channels (Instagram and WhatsApp)
        Instant lastMsg = conversation.getLastCustomerMessageAt();
        if (lastMsg == null) {
            // If lastCustomerMessageAt not explicitly set, fallback to lastMessageAt or created timestamp
            lastMsg = conversation.getLastMessageAt() != null ? conversation.getLastMessageAt() : conversation.getCreatedAt();
        }

        if (lastMsg == null) {
            return CrmDto.MessagingWindowResponse.builder()
                    .conversationId(conversation.getId())
                    .channel(channel)
                    .windowStatus("EXPIRED")
                    .remainingSeconds(0L)
                    .lastCustomerMessageAt(null)
                    .windowExpiresAt(null)
                    .canSendFreeform(false)
                    .canSendHumanAgent(false)
                    .policyDescription("No inbound customer messages recorded yet. User must engage before messaging.")
                    .build();
        }

        long elapsedSeconds = Math.max(0, Duration.between(lastMsg, now).getSeconds());

        // Inside standard 24-hour window
        if (elapsedSeconds < WINDOW_24H_SECONDS) {
            long remaining = WINDOW_24H_SECONDS - elapsedSeconds;
            Instant expiresAt = lastMsg.plusSeconds(WINDOW_24H_SECONDS);
            return CrmDto.MessagingWindowResponse.builder()
                    .conversationId(conversation.getId())
                    .channel(channel)
                    .windowStatus("ACTIVE_24H")
                    .remainingSeconds(remaining)
                    .lastCustomerMessageAt(lastMsg)
                    .windowExpiresAt(expiresAt)
                    .canSendFreeform(true)
                    .canSendHumanAgent(true)
                    .policyDescription("Active 24-hour customer care window. Freeform responses allowed.")
                    .build();
        }

        // Instagram allows 7-day extended window for Human Agent inquiries
        if (channel == ChannelType.INSTAGRAM && elapsedSeconds < WINDOW_7D_SECONDS) {
            long remaining = WINDOW_7D_SECONDS - elapsedSeconds;
            Instant expiresAt = lastMsg.plusSeconds(WINDOW_7D_SECONDS);
            return CrmDto.MessagingWindowResponse.builder()
                    .conversationId(conversation.getId())
                    .channel(channel)
                    .windowStatus("HUMAN_AGENT_EXTENDED_7D")
                    .remainingSeconds(remaining)
                    .lastCustomerMessageAt(lastMsg)
                    .windowExpiresAt(expiresAt)
                    .canSendFreeform(false)
                    .canSendHumanAgent(true)
                    .policyDescription("Standard 24h window closed. Human Agent response permitted up to 7 days on Instagram.")
                    .build();
        }

        // Beyond allowed windows
        return CrmDto.MessagingWindowResponse.builder()
                .conversationId(conversation.getId())
                .channel(channel)
                .windowStatus("EXPIRED")
                .remainingSeconds(0L)
                .lastCustomerMessageAt(lastMsg)
                .windowExpiresAt(lastMsg.plusSeconds(WINDOW_24H_SECONDS))
                .canSendFreeform(false)
                .canSendHumanAgent(false)
                .policyDescription(channel == ChannelType.WHATSAPP
                        ? "24-hour WhatsApp session expired. Template message required to re-initiate contact."
                        : "Customer session window closed. User must re-engage before messaging can resume.")
                .build();
    }

    @Override
    public boolean isMessageAllowed(Conversation conversation, boolean hasHumanAgentTag) {
        CrmDto.MessagingWindowResponse window = evaluateWindow(conversation);
        if ("UNRESTRICTED".equals(window.getWindowStatus()) || "ACTIVE_24H".equals(window.getWindowStatus())) {
            return true;
        }
        if ("HUMAN_AGENT_EXTENDED_7D".equals(window.getWindowStatus()) && hasHumanAgentTag) {
            return true;
        }
        return false;
    }

    @Override
    public void validateCanSend(Conversation conversation, boolean hasHumanAgentTag) {
        if (!isMessageAllowed(conversation, hasHumanAgentTag)) {
            CrmDto.MessagingWindowResponse window = evaluateWindow(conversation);
            log.warn("Attempted to send message outside Meta window for conversation {} [channel: {}, status: {}]",
                    conversation.getId(), conversation.getChannel(), window.getWindowStatus());
            throw new IllegalStateException("Cannot send message: " + window.getPolicyDescription());
        }
    }
}
