package com.autoflow.modules.workflow.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.exceptions.ValidationException;
import com.autoflow.modules.billing.service.EntitlementService;
import com.autoflow.modules.workflow.dto.*;
import com.autoflow.modules.workflow.engine.DagModel;
import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import com.autoflow.modules.workflow.entity.Workflow;
import com.autoflow.modules.workflow.entity.WorkflowVersion;
import com.autoflow.modules.workflow.repository.AutomationExecutionRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import com.autoflow.modules.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final AutomationExecutionRepository automationExecutionRepository;
    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowResponse createWorkflow(UUID organizationId, CreateWorkflowRequest request) {
        // Enforce plan quota
        entitlementService.assertCanCreateWorkflow(organizationId);

        Workflow workflow = Workflow.builder()
                .name(request.getName())
                .description(request.getDescription())
                .status("DRAFT")
                .build();
        workflow.setOrganizationId(organizationId);
        workflow = workflowRepository.save(workflow);

        String initialGraph = request.getInitialGraphDefinition();
        int versionNum = 1;
        if (initialGraph != null && !initialGraph.isBlank()) {
            try {
                DagModel dag = DagModel.fromJson(initialGraph, objectMapper);
                dag.validate();
            } catch (Exception e) {
                log.warn("Initial graph validation failed for workflow {}: {}", workflow.getId(), e.getMessage());
            }

            WorkflowVersion initialVersion = WorkflowVersion.builder()
                    .workflow(workflow)
                    .versionNumber(versionNum)
                    .graphDefinition(initialGraph)
                    .build();
            workflowVersionRepository.save(initialVersion);
            workflow.setActiveVersionNumber(versionNum);
            workflow = workflowRepository.save(workflow);
        }

        log.info("Created workflow {} ('{}') for org {}", workflow.getId(), workflow.getName(), organizationId);
        return WorkflowResponse.fromEntity(workflow, versionNum, initialGraph);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflows(UUID organizationId) {
        List<Workflow> workflows = workflowRepository.findByOrganizationId(organizationId);
        return workflows.stream().map(w -> {
            Optional<WorkflowVersion> latest = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(w.getId());
            return WorkflowResponse.fromEntity(
                    w,
                    latest.map(WorkflowVersion::getVersionNumber).orElse(w.getActiveVersionNumber()),
                    latest.map(WorkflowVersion::getGraphDefinition).orElse(null)
            );
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(UUID organizationId, UUID workflowId) {
        Workflow workflow = findOrgWorkflow(organizationId, workflowId);
        Optional<WorkflowVersion> latest = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId);
        return WorkflowResponse.fromEntity(
                workflow,
                latest.map(WorkflowVersion::getVersionNumber).orElse(workflow.getActiveVersionNumber()),
                latest.map(WorkflowVersion::getGraphDefinition).orElse(null)
        );
    }

    @Override
    @Transactional
    public WorkflowResponse updateWorkflow(UUID organizationId, UUID workflowId, UpdateWorkflowRequest request) {
        Workflow workflow = findOrgWorkflow(organizationId, workflowId);

        if (request.getName() != null && !request.getName().isBlank()) {
            workflow.setName(request.getName());
        }
        if (request.getDescription() != null) {
            workflow.setDescription(request.getDescription());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String statusUpper = request.getStatus().toUpperCase();
            if (List.of("DRAFT", "PUBLISHED", "PAUSED").contains(statusUpper)) {
                workflow.setStatus(statusUpper);
            }
        }

        workflow = workflowRepository.save(workflow);
        Optional<WorkflowVersion> latest = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId);
        return WorkflowResponse.fromEntity(
                workflow,
                latest.map(WorkflowVersion::getVersionNumber).orElse(workflow.getActiveVersionNumber()),
                latest.map(WorkflowVersion::getGraphDefinition).orElse(null)
        );
    }

    @Override
    @Transactional
    public WorkflowResponse saveWorkflowVersion(UUID organizationId, UUID workflowId, SaveWorkflowVersionRequest request) {
        Workflow workflow = findOrgWorkflow(organizationId, workflowId);

        // Validate graph syntax
        DagModel dag = DagModel.fromJson(request.getGraphDefinition(), objectMapper);
        dag.validate();

        int nextVersion = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        WorkflowVersion newVersion = WorkflowVersion.builder()
                .workflow(workflow)
                .versionNumber(nextVersion)
                .graphDefinition(request.getGraphDefinition())
                .build();
        workflowVersionRepository.save(newVersion);

        log.info("Saved version {} for workflow {} by org {}", nextVersion, workflowId, organizationId);
        return WorkflowResponse.fromEntity(workflow, nextVersion, request.getGraphDefinition());
    }

    @Override
    @Transactional
    public WorkflowResponse publishWorkflow(UUID organizationId, UUID workflowId) {
        Workflow workflow = findOrgWorkflow(organizationId, workflowId);

        WorkflowVersion latestVersion = workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId)
                .orElseThrow(() -> new ValidationException("Cannot publish workflow with no saved versions"));

        // Rigorous DAG validation: must have trigger, acyclic, valid nodes
        DagModel dag = DagModel.fromJson(latestVersion.getGraphDefinition(), objectMapper);
        dag.validate();

        workflow.setStatus("PUBLISHED");
        workflow.setActiveVersionNumber(latestVersion.getVersionNumber());
        workflow = workflowRepository.save(workflow);

        log.info("Published workflow {} at version {}", workflowId, latestVersion.getVersionNumber());
        return WorkflowResponse.fromEntity(workflow, latestVersion.getVersionNumber(), latestVersion.getGraphDefinition());
    }

    @Override
    @Transactional
    public void deleteWorkflow(UUID organizationId, UUID workflowId) {
        Workflow workflow = findOrgWorkflow(organizationId, workflowId);
        workflowRepository.delete(workflow);
        log.info("Deleted workflow {} for org {}", workflowId, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowExecutionResponse> getWorkflowExecutions(UUID organizationId, UUID workflowId) {
        // Verify ownership
        findOrgWorkflow(organizationId, workflowId);
        List<AutomationExecution> executions = automationExecutionRepository.findByWorkflowIdOrderByStartedAtDesc(workflowId);
        return executions.stream().map(WorkflowExecutionResponse::fromEntity).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkflowExecutionResponse retryWorkflowExecution(UUID organizationId, UUID executionId) {
        AutomationExecution execution = automationExecutionRepository.findByIdAndOrganizationId(executionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationExecution", executionId));

        if (execution.getStatus() != ExecutionStatus.FAILED) {
            throw new IllegalStateException("Only failed workflow executions can be retried (current status: " + execution.getStatus() + ")");
        }

        if (execution.getRetryCount() >= 3) {
            throw new IllegalStateException("Maximum retry attempts (3) exceeded for execution " + executionId);
        }

        execution.setRetryCount(execution.getRetryCount() + 1);
        execution.setStatus(ExecutionStatus.RETRYING);
        execution.setErrorMessage(null);
        AutomationExecution updated = automationExecutionRepository.save(execution);

        log.info("Queued execution {} for retry (attempt #{}) under org {}", executionId, updated.getRetryCount(), organizationId);
        return WorkflowExecutionResponse.fromEntity(updated);
    }

    private Workflow findOrgWorkflow(UUID organizationId, UUID workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", workflowId));
        if (!workflow.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Workflow", workflowId);
        }
        return workflow;
    }
}
