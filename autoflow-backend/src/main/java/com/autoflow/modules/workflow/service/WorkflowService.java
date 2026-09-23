package com.autoflow.modules.workflow.service;

import com.autoflow.modules.workflow.dto.*;

import java.util.List;
import java.util.UUID;

public interface WorkflowService {

    WorkflowResponse createWorkflow(UUID organizationId, CreateWorkflowRequest request);

    List<WorkflowResponse> listWorkflows(UUID organizationId);

    WorkflowResponse getWorkflow(UUID organizationId, UUID workflowId);

    WorkflowResponse updateWorkflow(UUID organizationId, UUID workflowId, UpdateWorkflowRequest request);

    WorkflowResponse saveWorkflowVersion(UUID organizationId, UUID workflowId, SaveWorkflowVersionRequest request);

    WorkflowResponse publishWorkflow(UUID organizationId, UUID workflowId);

    void deleteWorkflow(UUID organizationId, UUID workflowId);

    List<WorkflowExecutionResponse> getWorkflowExecutions(UUID organizationId, UUID workflowId);

    WorkflowExecutionResponse retryWorkflowExecution(UUID organizationId, UUID executionId);
}
