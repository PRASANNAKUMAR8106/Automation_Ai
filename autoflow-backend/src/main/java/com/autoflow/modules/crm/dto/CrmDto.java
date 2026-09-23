package com.autoflow.modules.crm.dto;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.LeadStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CrmDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContactResponse {
        private UUID id;
        private ChannelType channel;
        private String externalId;
        private String username;
        private String fullName;
        private String email;
        private String phone;
        private LeadStatus leadStatus;
        private int leadScore;
        private List<String> tags;
        private Instant lastInteractionAt;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationResponse {
        private UUID id;
        private ContactResponse contact;
        private ChannelType channel;

        @JsonProperty("isResolved")
        private boolean isResolved;

        @JsonProperty("resolved")
        public boolean getResolved() {
            return isResolved;
        }

        private Instant lastMessageAt;
        private Instant lastCustomerMessageAt;
        private String windowStatus;
        private Long windowRemainingSeconds;
        private String lastMessageSnippet;
        private int unreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageResponse {
        private UUID id;
        private UUID conversationId;
        private String direction;
        private String senderType;
        private String messageType;
        private String content;
        private String mediaUrl;
        private String deliveryStatus;
        private Instant sentAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddTagsRequest {
        @NotEmpty(message = "Tags list must not be empty")
        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendAgentReplyRequest {
        @NotBlank(message = "Message content must not be blank")
        private String content;
        private String mediaUrl;
        private boolean humanAgentTag;

        public SendAgentReplyRequest(String content, String mediaUrl) {
            this.content = content;
            this.mediaUrl = mediaUrl;
            this.humanAgentTag = false;
        }

        public SendAgentReplyRequest(String content) {
            this(content, null, false);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessagingWindowResponse {
        private UUID conversationId;
        private ChannelType channel;
        private String windowStatus; // ACTIVE_24H, HUMAN_AGENT_EXTENDED_7D, EXPIRED, UNRESTRICTED
        private Long remainingSeconds;
        private Instant lastCustomerMessageAt;
        private Instant windowExpiresAt;
        private boolean canSendFreeform;
        private boolean canSendHumanAgent;
        private String policyDescription;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypingIndicatorRequest {
        private boolean isTyping;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolveConversationRequest {
        private boolean resolved;
    }
}
