package com.autoflow.modules.workflow.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.workflow.dto.*;
import com.autoflow.modules.workflow.service.WorkflowService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflows & Automations", description = "Endpoints for creating, editing, versioning, publishing, and inspecting automation workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    @Operation(summary = "Create Workflow", description = "Creates a new automation workflow under the current tenant organization")
    public ResponseEntity<ApiResponse<WorkflowResponse>> createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse response = workflowService.createWorkflow(orgId, request);
        return ResponseEntity.ok(ApiResponse.ok("Workflow created successfully", response));
    }

    @GetMapping
    @Operation(summary = "List Workflows", description = "Returns all automation workflows for the tenant organization")
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> listWorkflows() {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<WorkflowResponse> response = workflowService.listWorkflows(orgId);
        return ResponseEntity.ok(ApiResponse.ok("Workflows retrieved successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Workflow Details", description = "Retrieves workflow configuration and latest DAG definition")
    public ResponseEntity<ApiResponse<WorkflowResponse>> getWorkflow(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse response = workflowService.getWorkflow(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Workflow retrieved successfully", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Workflow Metadata", description = "Updates workflow name, description, or status (DRAFT, PUBLISHED, PAUSED)")
    public ResponseEntity<ApiResponse<WorkflowResponse>> updateWorkflow(
            @PathVariable UUID id,
            @RequestBody UpdateWorkflowRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse response = workflowService.updateWorkflow(orgId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Workflow updated successfully", response));
    }

    @PostMapping("/{id}/versions")
    @Operation(summary = "Save Workflow Version", description = "Validates and saves a new DAG definition version")
    public ResponseEntity<ApiResponse<WorkflowResponse>> saveWorkflowVersion(
            @PathVariable UUID id,
            @Valid @RequestBody SaveWorkflowVersionRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse response = workflowService.saveWorkflowVersion(orgId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Workflow version saved successfully", response));
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish Workflow", description = "Validates DAG topology and activates the latest version for live execution")
    public ResponseEntity<ApiResponse<WorkflowResponse>> publishWorkflow(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse response = workflowService.publishWorkflow(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Workflow published successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Workflow", description = "Deletes the workflow and associated versions")
    public ResponseEntity<ApiResponse<Void>> deleteWorkflow(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        workflowService.deleteWorkflow(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Workflow deleted successfully", null));
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "Get Execution Audit Logs", description = "Returns historical execution runs and statuses for this workflow")
    public ResponseEntity<ApiResponse<List<WorkflowExecutionResponse>>> getWorkflowExecutions(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<WorkflowExecutionResponse> response = workflowService.getWorkflowExecutions(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Workflow executions retrieved successfully", response));
    }
}
