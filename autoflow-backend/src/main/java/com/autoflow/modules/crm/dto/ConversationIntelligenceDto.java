package com.autoflow.modules.crm.dto;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

public class ConversationIntelligenceDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlaPolicyRequest {
        @NotBlank(message = "Policy name is required")
        private String name;
        private ChannelType channel;
        private ConversationPriority priority;
        @Min(value = 60, message = "First response SLA must be at least 60 seconds")
        private int firstResponseTimeSeconds;
        @Min(value = 60, message = "Resolution SLA must be at least 60 seconds")
        private int resolutionTimeSeconds;
        private RoutingPolicy routingPolicy;
        @Builder.Default
        private boolean active = true;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlaPolicyResponse {
        private UUID id;
        private String name;
        private ChannelType channel;
        private ConversationPriority priority;
        private int firstResponseTimeSeconds;
        private int resolutionTimeSeconds;
        private RoutingPolicy routingPolicy;
        private boolean active;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceAnalyticsResponse {
        private Double averageFirstResponseTimeMinutes;
        private Double averageResolutionTimeMinutes;
        private Double slaFirstResponseComplianceRate;
        private Double slaResolutionComplianceRate;
        private Long totalConversations;
        private Long resolvedConversations;
        private Long activeConversations;
        private Double averageCsatRating;
        private Long totalCsatResponses;
        private Double aiAutoPilotDeflectionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitCsatRequest {
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        private Integer rating;
        private String feedbackText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CsatSurveyResponse {
        private UUID id;
        private UUID conversationId;
        private UUID contactId;
        private Integer rating;
        private String feedbackText;
        private String status;
        private Instant dispatchedAt;
        private Instant respondedAt;
    }
}
