package com.autoflow.modules.knowledge.dto;

import com.autoflow.modules.knowledge.entity.KnowledgeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class KnowledgeDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateArticleRequest {
        @NotBlank(message = "Title is required")
        private String title;

        @NotNull(message = "Category is required")
        @Builder.Default
        private KnowledgeCategory category = KnowledgeCategory.GENERAL;

        @NotBlank(message = "Content is required")
        private String content;

        private List<String> tags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateArticleRequest {
        private String title;
        private KnowledgeCategory category;
        private String content;
        private List<String> tags;
        private Boolean active;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArticleResponse {
        private UUID id;
        private UUID organizationId;
        private String title;
        private KnowledgeCategory category;
        private String content;
        private List<String> tags;
        private boolean active;
        private int usageCount;
        private String citationSnippet;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
