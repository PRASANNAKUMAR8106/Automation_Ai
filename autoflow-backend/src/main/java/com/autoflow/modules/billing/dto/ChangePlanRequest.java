package com.autoflow.modules.billing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePlanRequest {

    @NotBlank(message = "Target plan code is required")
    private String targetPlanCode;
}
