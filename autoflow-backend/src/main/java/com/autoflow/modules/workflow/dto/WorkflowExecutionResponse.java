package com.autoflow.modules.workflow.dto;

import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowExecutionResponse {

    private UUID id;
    private UUID organizationId;
    private UUID workflowId;
    private String workflowName;
    private String triggerType;
    private String triggerEventId;
    private ExecutionStatus status;
    private String currentNodeId;
    private String executionContext;
    private String errorMessage;
    private int retryCount;
    private Instant startedAt;
    private Instant completedAt;

    public static WorkflowExecutionResponse fromEntity(AutomationExecution exec) {
        if (exec == null) return null;
        return WorkflowExecutionResponse.builder()
                .id(exec.getId())
                .organizationId(exec.getOrganizationId())
                .workflowId(exec.getWorkflow() != null ? exec.getWorkflow().getId() : null)
                .workflowName(exec.getWorkflow() != null ? exec.getWorkflow().getName() : null)
                .triggerType(exec.getTriggerType())
                .triggerEventId(exec.getTriggerEventId())
                .status(exec.getStatus())
                .currentNodeId(exec.getCurrentNodeId())
                .executionContext(exec.getExecutionContext())
                .errorMessage(exec.getErrorMessage())
                .retryCount(exec.getRetryCount())
                .startedAt(exec.getStartedAt())
                .completedAt(exec.getCompletedAt())
                .build();
    }
}
