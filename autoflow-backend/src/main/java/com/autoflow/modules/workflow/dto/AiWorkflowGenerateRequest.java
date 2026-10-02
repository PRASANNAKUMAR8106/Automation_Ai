package com.autoflow.modules.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkflowGenerateRequest {

    @NotBlank(message = "Prompt description is required")
    private String prompt;

    private String name;
}
