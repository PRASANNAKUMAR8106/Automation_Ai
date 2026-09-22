package com.autoflow.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveWorkflowVersionRequest {

    @NotBlank(message = "Graph definition is required")
    private String graphDefinition;
}
