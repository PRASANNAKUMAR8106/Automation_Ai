package com.autoflow.modules.analytics;

import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelsOverviewResponse;
import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.WorkflowFunnelResponse;
import com.autoflow.modules.analytics.service.AnalyticsServiceImpl;
import com.autoflow.modules.billing.service.EntitlementService;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Funnel Analytics Service Tests")
class FunnelAnalyticsServiceTest {

    @Mock
    private AutomationExecutionRepository automationExecutionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowVersionRepository workflowVersionRepository;

    @Mock
    private EntitlementService entitlementService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnalyticsServiceImpl analyticsService;
    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsServiceImpl(
                automationExecutionRepository,
                messageRepository,
                contactRepository,
                workflowRepository,
                workflowVersionRepository,
                entitlementService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should calculate node-by-node conversion funnel and identify drop-off bottlenecks")
    void shouldCalculateWorkflowFunnelAccurately() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder()
                .name("Comment to DM Funnel")
                .status("PUBLISHED")
                .activeVersionNumber(1)
                .build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        String graphJson = """
        {
          "nodes": [
            { "id": "n_trigger", "type": "TRIGGER_INSTAGRAM_COMMENT" },
            { "id": "n_reply", "type": "ACTION_PUBLIC_COMMENT_REPLY" },
            { "id": "n_dm", "type": "ACTION_SEND_DM" }
          ],
          "edges": [
            { "id": "e1", "from": "n_trigger", "to": "n_reply" },
            { "id": "e2", "from": "n_reply", "to": "n_dm" }
          ]
        }
        """;

        WorkflowVersion version = WorkflowVersion.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .versionNumber(1)
                .graphDefinition(graphJson)
                .build();

        when(workflowRepository.findByIdAndOrganizationId(workflowId, testOrgId)).thenReturn(Optional.of(workflow));
        when(workflowVersionRepository.findByWorkflowIdAndVersionNumber(workflowId, 1)).thenReturn(Optional.of(version));

        // 10 Executions:
        // 7 Succeeded (traversed all 3 steps)
        // 2 Failed at n_reply (step 2)
        // 1 Failed at n_trigger (step 1)
        List<AutomationExecution> executions = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            executions.add(AutomationExecution.builder()
                    .id(UUID.randomUUID())
                    .status(ExecutionStatus.SUCCESS)
                    .currentNodeId("n_dm")
                    .build());
        }
        for (int i = 0; i < 2; i++) {
            executions.add(AutomationExecution.builder()
                    .id(UUID.randomUUID())
                    .status(ExecutionStatus.FAILED)
                    .currentNodeId("n_reply")
                    .build());
        }
        executions.add(AutomationExecution.builder()
                .id(UUID.randomUUID())
                .status(ExecutionStatus.FAILED)
                .currentNodeId("n_trigger")
                .build());

        when(automationExecutionRepository.findByWorkflowIdAndOrganizationIdOrderByStartedAtDesc(workflowId, testOrgId))
                .thenReturn(executions);

        WorkflowFunnelResponse response = analyticsService.getWorkflowFunnel(testOrgId, workflowId);

        assertNotNull(response);
        assertEquals(workflowId, response.getWorkflowId());
        assertEquals("Comment to DM Funnel", response.getWorkflowName());
        assertEquals(10, response.getTotalRuns());
        assertEquals(7, response.getSuccessfulRuns());
        assertEquals(3, response.getFailedRuns());
        assertEquals(70.0, response.getOverallConversionRate());

        assertEquals(3, response.getSteps().size());

        // Step 1: n_trigger
        assertEquals("n_trigger", response.getSteps().get(0).getNodeId());
        assertEquals(10, response.getSteps().get(0).getReachedCount());
        assertEquals(1, response.getSteps().get(0).getDropOffCount());
        assertEquals(100.0, response.getSteps().get(0).getConversionPercentage());
        assertEquals(10.0, response.getSteps().get(0).getDropOffPercentage());

        // Step 2: n_reply
        assertEquals("n_reply", response.getSteps().get(1).getNodeId());
        assertEquals(9, response.getSteps().get(1).getReachedCount());
        assertEquals(2, response.getSteps().get(1).getDropOffCount());
        assertEquals(90.0, response.getSteps().get(1).getConversionPercentage());

        // Step 3: n_dm
        assertEquals("n_dm", response.getSteps().get(2).getNodeId());
        assertEquals(7, response.getSteps().get(2).getReachedCount());
        assertEquals(0, response.getSteps().get(2).getDropOffCount());
        assertEquals(70.0, response.getSteps().get(2).getConversionPercentage());

        // Bottleneck node should be n_reply (2 drop offs / 9 = 22.2%)
        assertEquals("n_reply", response.getBottleneckNodeId());
    }

    @Test
    @DisplayName("Should handle empty executions without division-by-zero")
    void shouldHandleEmptyExecutionsGracefully() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder()
                .name("New Workflow")
                .status("DRAFT")
                .build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        when(workflowRepository.findByIdAndOrganizationId(workflowId, testOrgId)).thenReturn(Optional.of(workflow));
        when(automationExecutionRepository.findByWorkflowIdAndOrganizationIdOrderByStartedAtDesc(workflowId, testOrgId))
                .thenReturn(List.of());

        WorkflowFunnelResponse response = analyticsService.getWorkflowFunnel(testOrgId, workflowId);

        assertNotNull(response);
        assertEquals(0, response.getTotalRuns());
        assertEquals(0.0, response.getOverallConversionRate());
        assertTrue(response.getSteps().isEmpty());
    }

    @Test
    @DisplayName("Should calculate multi-workflow aggregate funnels overview")
    void shouldCalculateFunnelsOverview() {
        UUID wf1 = UUID.randomUUID();
        UUID wf2 = UUID.randomUUID();

        Workflow w1 = Workflow.builder().name("W1").status("PUBLISHED").build();
        w1.setId(wf1);
        w1.setOrganizationId(testOrgId);

        Workflow w2 = Workflow.builder().name("W2").status("PUBLISHED").build();
        w2.setId(wf2);
        w2.setOrganizationId(testOrgId);

        when(workflowRepository.findByOrganizationId(testOrgId)).thenReturn(List.of(w1, w2));
        when(workflowRepository.findByIdAndOrganizationId(wf1, testOrgId)).thenReturn(Optional.of(w1));
        when(workflowRepository.findByIdAndOrganizationId(wf2, testOrgId)).thenReturn(Optional.of(w2));

        when(automationExecutionRepository.findByWorkflowIdAndOrganizationIdOrderByStartedAtDesc(eq(wf1), eq(testOrgId)))
                .thenReturn(List.of(AutomationExecution.builder().id(UUID.randomUUID()).status(ExecutionStatus.SUCCESS).build()));

        when(automationExecutionRepository.findByWorkflowIdAndOrganizationIdOrderByStartedAtDesc(eq(wf2), eq(testOrgId)))
                .thenReturn(List.of());

        FunnelsOverviewResponse overview = analyticsService.getFunnelsOverview(testOrgId);

        assertNotNull(overview);
        assertEquals(2, overview.getTotalPipelines());
        assertEquals(1, overview.getAggregateRuns());
        assertEquals(1, overview.getAggregateConversions());
        assertEquals(100.0, overview.getAverageConversionRate());
        assertEquals(wf1, overview.getTopConvertingWorkflowId());
    }
}
