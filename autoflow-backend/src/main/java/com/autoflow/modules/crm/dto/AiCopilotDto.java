package com.autoflow.modules.crm.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

public class AiCopilotDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiSuggestionRequest {
        private String customInstruction;
        private String personaOverride;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiSuggestionResponse {
        private UUID conversationId;
        private String suggestedReply;
        private double confidenceScore;
        private boolean requiresHumanHandoff;
        private String humanHandoffReason;
        private List<String> sourceArticleTitles;
        private String activeModel;
    }
}
