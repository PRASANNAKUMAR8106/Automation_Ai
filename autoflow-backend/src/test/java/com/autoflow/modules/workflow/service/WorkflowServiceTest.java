package com.autoflow.modules.workflow.service;

import com.autoflow.common.exceptions.QuotaExceededException;
import com.autoflow.modules.billing.service.EntitlementService;
import com.autoflow.modules.workflow.dto.CreateWorkflowRequest;
import com.autoflow.modules.workflow.dto.SaveWorkflowVersionRequest;
import com.autoflow.modules.workflow.dto.WorkflowResponse;
import com.autoflow.modules.workflow.entity.Workflow;
import com.autoflow.modules.workflow.entity.WorkflowVersion;
import com.autoflow.modules.workflow.repository.AutomationExecutionRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import com.autoflow.modules.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import com.autoflow.modules.workflow.dto.WorkflowExecutionResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Workflow Service Management Tests")
class WorkflowServiceTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowVersionRepository workflowVersionRepository;

    @Mock
    private AutomationExecutionRepository automationExecutionRepository;

    @Mock
    private EntitlementService entitlementService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowServiceImpl workflowService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowServiceImpl(
                workflowRepository,
                workflowVersionRepository,
                automationExecutionRepository,
                entitlementService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should create workflow and enforce plan quota")
    void shouldCreateWorkflowWithQuotaCheck() {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .name("Comment to DM Magnet")
                .description("Auto reply with PDF guide")
                .initialGraphDefinition("{\"nodes\":[{\"id\":\"n1\",\"type\":\"TRIGGER_INSTAGRAM_COMMENT\"}],\"edges\":[]}")
                .build();

        doNothing().when(entitlementService).assertCanCreateWorkflow(testOrgId);
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> {
            Workflow w = invocation.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WorkflowResponse response = workflowService.createWorkflow(testOrgId, request);

        verify(entitlementService).assertCanCreateWorkflow(testOrgId);
        assertNotNull(response);
        assertEquals("Comment to DM Magnet", response.getName());
        assertEquals("DRAFT", response.getStatus());
        assertEquals(1, response.getActiveVersionNumber());
        verify(workflowVersionRepository).save(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("Should block workflow creation when plan quota is exceeded")
    void shouldBlockCreationWhenQuotaExceeded() {
        doThrow(new QuotaExceededException("max_automations", 2, 2))
                .when(entitlementService).assertCanCreateWorkflow(testOrgId);

        CreateWorkflowRequest request = CreateWorkflowRequest.builder().name("Excess Workflow").build();

        assertThrows(QuotaExceededException.class, () -> workflowService.createWorkflow(testOrgId, request));
        verify(workflowRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should save new version and increment version number")
    void shouldSaveNewVersionIncremented() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder().name("Test Flow").status("DRAFT").build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        WorkflowVersion existingV1 = WorkflowVersion.builder().workflow(workflow).versionNumber(1).build();
        when(workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId))
                .thenReturn(Optional.of(existingV1));

        String validDagJson = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"TRIGGER_INSTAGRAM_COMMENT\"}],\"edges\":[]}";
        SaveWorkflowVersionRequest req = new SaveWorkflowVersionRequest(validDagJson);

        WorkflowResponse response = workflowService.saveWorkflowVersion(testOrgId, workflowId, req);

        ArgumentCaptor<WorkflowVersion> captor = ArgumentCaptor.forClass(WorkflowVersion.class);
        verify(workflowVersionRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getVersionNumber());
        assertEquals(2, response.getLatestVersionNumber());
    }

    @Test
    @DisplayName("Should publish workflow and set active version number")
    void shouldPublishWorkflow() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder().name("Test Flow").status("DRAFT").build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(workflow));
        String validDagJson = "{\"nodes\":[{\"id\":\"n1\",\"type\":\"TRIGGER_INSTAGRAM_COMMENT\"}],\"edges\":[]}";
        WorkflowVersion latest = WorkflowVersion.builder().workflow(workflow).versionNumber(3).graphDefinition(validDagJson).build();
        when(workflowVersionRepository.findTopByWorkflowIdOrderByVersionNumberDesc(workflowId))
                .thenReturn(Optional.of(latest));
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(i -> i.getArgument(0));

        WorkflowResponse response = workflowService.publishWorkflow(testOrgId, workflowId);

        assertEquals("PUBLISHED", response.getStatus());
        assertEquals(3, response.getActiveVersionNumber());
        verify(workflowRepository).save(workflow);
    }

    @Test
    @DisplayName("Should successfully retry failed workflow execution")
    void shouldRetryFailedExecutionSuccessfully() {
        UUID executionId = UUID.randomUUID();
        AutomationExecution failedExecution = AutomationExecution.builder()
                .id(executionId)
                .organizationId(testOrgId)
                .status(ExecutionStatus.FAILED)
                .retryCount(0)
                .errorMessage("API timeout")
                .build();

        when(automationExecutionRepository.findByIdAndOrganizationId(executionId, testOrgId))
                .thenReturn(Optional.of(failedExecution));
        when(automationExecutionRepository.save(any(AutomationExecution.class)))
                .thenAnswer(i -> i.getArgument(0));

        WorkflowExecutionResponse resp = workflowService.retryWorkflowExecution(testOrgId, executionId);

        assertNotNull(resp);
        assertEquals(ExecutionStatus.RETRYING, resp.getStatus());
        assertEquals(1, resp.getRetryCount());
        assertNull(resp.getErrorMessage());
        verify(automationExecutionRepository).save(failedExecution);
    }

    @Test
    @DisplayName("Should reject retry when execution status is not FAILED")
    void shouldRejectRetryWhenStatusNotFailed() {
        UUID executionId = UUID.randomUUID();
        AutomationExecution successExecution = AutomationExecution.builder()
                .id(executionId)
                .organizationId(testOrgId)
                .status(ExecutionStatus.SUCCESS)
                .retryCount(0)
                .build();

        when(automationExecutionRepository.findByIdAndOrganizationId(executionId, testOrgId))
                .thenReturn(Optional.of(successExecution));

        assertThrows(IllegalStateException.class, () ->
                workflowService.retryWorkflowExecution(testOrgId, executionId));
        verify(automationExecutionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject retry when max retry limit is exceeded")
    void shouldRejectRetryWhenMaxRetriesExceeded() {
        UUID executionId = UUID.randomUUID();
        AutomationExecution maxedExecution = AutomationExecution.builder()
                .id(executionId)
                .organizationId(testOrgId)
                .status(ExecutionStatus.FAILED)
                .retryCount(3)
                .build();

        when(automationExecutionRepository.findByIdAndOrganizationId(executionId, testOrgId))
                .thenReturn(Optional.of(maxedExecution));

        assertThrows(IllegalStateException.class, () ->
                workflowService.retryWorkflowExecution(testOrgId, executionId));
        verify(automationExecutionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject retry when execution does not belong to tenant")
    void shouldRejectRetryWhenCrossTenant() {
        UUID executionId = UUID.randomUUID();
        when(automationExecutionRepository.findByIdAndOrganizationId(executionId, testOrgId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                workflowService.retryWorkflowExecution(testOrgId, executionId));
    }
}

