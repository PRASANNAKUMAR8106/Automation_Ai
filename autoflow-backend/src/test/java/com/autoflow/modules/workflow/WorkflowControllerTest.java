package com.autoflow.modules.workflow;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.workflow.controller.WorkflowController;
import com.autoflow.modules.workflow.dto.*;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import com.autoflow.modules.workflow.service.WorkflowService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkflowController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WorkflowController Web MVC Tests")
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkflowService workflowService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testWorkflowId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/workflows creates new workflow")
    void testCreateWorkflow() throws Exception {
        CreateWorkflowRequest request = CreateWorkflowRequest.builder()
                .name("IG Lead Magnet")
                .description("Delivers guide on comment")
                .build();

        WorkflowResponse response = WorkflowResponse.builder()
                .id(testWorkflowId)
                .organizationId(testOrgId)
                .name("IG Lead Magnet")
                .status("DRAFT")
                .build();

        when(workflowService.createWorkflow(eq(testOrgId), any(CreateWorkflowRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workflows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testWorkflowId.toString()))
                .andExpect(jsonPath("$.data.name").value("IG Lead Magnet"));
    }

    @Test
    @DisplayName("GET /api/v1/workflows returns list of tenant workflows")
    void testListWorkflows() throws Exception {
        WorkflowResponse item = WorkflowResponse.builder()
                .id(testWorkflowId)
                .organizationId(testOrgId)
                .name("Demo Workflow")
                .status("PUBLISHED")
                .activeVersionNumber(1)
                .build();

        when(workflowService.listWorkflows(testOrgId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(testWorkflowId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("POST /api/v1/workflows/{id}/publish publishes workflow")
    void testPublishWorkflow() throws Exception {
        WorkflowResponse response = WorkflowResponse.builder()
                .id(testWorkflowId)
                .organizationId(testOrgId)
                .status("PUBLISHED")
                .activeVersionNumber(2)
                .build();

        when(workflowService.publishWorkflow(testOrgId, testWorkflowId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/workflows/" + testWorkflowId + "/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.activeVersionNumber").value(2));
    }

    @Test
    @DisplayName("DELETE /api/v1/workflows/{id} deletes workflow")
    void testDeleteWorkflow() throws Exception {
        mockMvc.perform(delete("/api/v1/workflows/" + testWorkflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(workflowService).deleteWorkflow(testOrgId, testWorkflowId);
    }

    @Test
    @DisplayName("GET /api/v1/workflows/{id}/executions returns execution history")
    void testGetWorkflowExecutions() throws Exception {
        WorkflowExecutionResponse exec = WorkflowExecutionResponse.builder()
                .id(UUID.randomUUID())
                .workflowId(testWorkflowId)
                .triggerType("TRIGGER_INSTAGRAM_COMMENT")
                .status(ExecutionStatus.SUCCESS)
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        when(workflowService.getWorkflowExecutions(testOrgId, testWorkflowId)).thenReturn(List.of(exec));

        mockMvc.perform(get("/api/v1/workflows/" + testWorkflowId + "/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"));
    }
}
