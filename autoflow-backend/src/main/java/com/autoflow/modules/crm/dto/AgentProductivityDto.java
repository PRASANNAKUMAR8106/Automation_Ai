package com.autoflow.modules.crm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AgentProductivityDto {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CannedResponseRequest {
        @NotBlank(message = "Shortcut is required")
        private String shortcut;

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Content is required")
        private String content;

        private String category;
        private Boolean isShared;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CannedResponseResponse {
        private UUID id;
        private String shortcut;
        private String title;
        private String content;
        private String category;
        private boolean isShared;
        private UUID createdByUserId;
        private int usageCount;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterpolateRequest {
        @NotBlank(message = "Content is required")
        private String content;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterpolatedResponse {
        private String originalContent;
        private String interpolatedContent;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrmMacroRequest {
        @NotBlank(message = "Name is required")
        private String name;

        private String description;

        @NotBlank(message = "actionsJson is required")
        private String actionsJson;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CrmMacroResponse {
        private UUID id;
        private String name;
        private String description;
        private String actionsJson;
        private UUID createdByUserId;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplyMacroResult {
        private UUID macroId;
        private String macroName;
        private boolean success;
        private List<String> actionsExecuted;
        private String outboundMessageSnippet;
        private boolean windowExpiredSkipped;
        private boolean suppressionSkipped;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentPresenceHeartbeatRequest {
        @NotBlank(message = "Action is required (VIEWING, TYPING, LEFT)")
        private String action;

        private String userEmail;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentPresenceDto {
        private UUID userId;
        private String userEmail;
        private String action; // VIEWING, TYPING
        private Instant lastActiveAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostInternalNoteRequest {
        @NotBlank(message = "Content is required")
        private String content;

        private String noteType; // INTERNAL_NOTE, SUPERVISOR_WHISPER
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalNoteResponse {
        private UUID id;
        private UUID conversationId;
        private UUID authorUserId;
        private String authorEmail;
        private String noteType;
        private String content;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineEventDto {
        private UUID id;
        private String category; // MESSAGE, INTERNAL_NOTE, SLA_EVENT, AI_ACTION, CSAT, LEAD_SCORE
        private String eventType;
        private String summary;
        private String actor;
        private Instant timestamp;
        private Map<String, Object> metadata;
    }
}
