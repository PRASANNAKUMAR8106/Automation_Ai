package com.autoflow.modules.workflow.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWorkflowRequest {

    private String name;

    private String description;

    private String status; // DRAFT, PUBLISHED, PAUSED
}
