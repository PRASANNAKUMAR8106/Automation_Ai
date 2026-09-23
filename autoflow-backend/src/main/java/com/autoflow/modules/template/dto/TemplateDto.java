package com.autoflow.modules.template.dto;

import com.autoflow.modules.template.entity.Template;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TemplateDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateResponse {
        private UUID id;
        private String name;
        private String category;
        private String description;
        private String icon;
        private List<String> tags;
        private String graphDefinition;
        private boolean isFeatured;
        private Instant createdAt;

        public static TemplateResponse fromEntity(Template entity) {
            return TemplateResponse.builder()
                    .id(entity.getId())
                    .name(entity.getName())
                    .category(entity.getCategory())
                    .description(entity.getDescription())
                    .icon(entity.getIcon())
                    .tags(entity.getTags())
                    .graphDefinition(entity.getGraphDefinition())
                    .isFeatured(entity.isFeatured())
                    .createdAt(entity.getCreatedAt())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InstantiateTemplateRequest {
        private String workflowName;
        private String description;
    }
}
