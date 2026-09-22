package com.autoflow.modules.workflow.dto;

import com.autoflow.modules.workflow.entity.Workflow;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowResponse {

    private UUID id;
    private UUID organizationId;
    private String name;
    private String description;
    private String status;
    private Integer activeVersionNumber;
    private Integer latestVersionNumber;
    private String graphDefinition;
    private Instant createdAt;
    private Instant updatedAt;

    public static WorkflowResponse fromEntity(Workflow w, Integer latestVersion, String graphDef) {
        if (w == null) return null;
        return WorkflowResponse.builder()
                .id(w.getId())
                .organizationId(w.getOrganizationId())
                .name(w.getName())
                .description(w.getDescription())
                .status(w.getStatus())
                .activeVersionNumber(w.getActiveVersionNumber())
                .latestVersionNumber(latestVersion)
                .graphDefinition(graphDef)
                .createdAt(w.getCreatedAt())
                .updatedAt(w.getUpdatedAt())
                .build();
    }
}
