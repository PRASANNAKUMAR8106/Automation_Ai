package com.autoflow.modules.analytics;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.analytics.controller.AnalyticsController;
import com.autoflow.modules.analytics.dto.AnalyticsOverviewResponse;
import com.autoflow.modules.analytics.service.AnalyticsService;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.security.TenantContext;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AnalyticsController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AnalyticsController Web MVC Tests")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/analytics/overview returns aggregated metrics")
    void testGetOverview() throws Exception {
        AnalyticsOverviewResponse overview = AnalyticsOverviewResponse.builder()
                .commentsProcessed(1428)
                .dmsDispatched(1392)
                .inboundMessages(1800)
                .outboundMessages(1392)
                .totalMessages(3192)
                .leadsCaptured(384)
                .activeWorkflows(4)
                .maxWorkflows(20)
                .channelBreakdown(Map.of("INSTAGRAM", 250L, "WHATSAPP", 134L))
                .build();

        when(analyticsService.getOverview(testOrgId)).thenReturn(overview);

        mockMvc.perform(get("/api/v1/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.commentsProcessed").value(1428))
                .andExpect(jsonPath("$.data.dmsDispatched").value(1392))
                .andExpect(jsonPath("$.data.leadsCaptured").value(384))
                .andExpect(jsonPath("$.data.activeWorkflows").value(4))
                .andExpect(jsonPath("$.data.maxWorkflows").value(20))
                .andExpect(jsonPath("$.data.channelBreakdown.INSTAGRAM").value(250));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/funnels/{id} returns workflow conversion funnel")
    void testGetWorkflowFunnel() throws Exception {
        UUID wfId = UUID.randomUUID();
        com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.WorkflowFunnelResponse funnel =
                com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.WorkflowFunnelResponse.builder()
                        .workflowId(wfId)
                        .workflowName("Comment Lead Magnet")
                        .status("PUBLISHED")
                        .totalRuns(100)
                        .successfulRuns(85)
                        .failedRuns(15)
                        .overallConversionRate(85.0)
                        .bottleneckNodeId("node_dm")
                        .bottleneckDropOffRate(12.0)
                        .steps(java.util.List.of(
                                com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelStepMetric.builder()
                                        .nodeId("node_trig")
                                        .nodeLabel("Instagram Comment Trigger")
                                        .nodeType("TRIGGER_INSTAGRAM_COMMENT")
                                        .stepIndex(1)
                                        .reachedCount(100)
                                        .dropOffCount(3)
                                        .conversionPercentage(100.0)
                                        .stepConversionPercentage(100.0)
                                        .dropOffPercentage(3.0)
                                        .avgDurationMs(120)
                                        .build()
                        ))
                        .build();

        when(analyticsService.getWorkflowFunnel(testOrgId, wfId)).thenReturn(funnel);

        mockMvc.perform(get("/api/v1/analytics/funnels/" + wfId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.workflowId").value(wfId.toString()))
                .andExpect(jsonPath("$.data.totalRuns").value(100))
                .andExpect(jsonPath("$.data.successfulRuns").value(85))
                .andExpect(jsonPath("$.data.overallConversionRate").value(85.0))
                .andExpect(jsonPath("$.data.steps[0].nodeId").value("node_trig"));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/funnels/overview returns aggregated pipelines")
    void testGetFunnelsOverview() throws Exception {
        com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelsOverviewResponse overview =
                com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelsOverviewResponse.builder()
                        .totalPipelines(3)
                        .aggregateRuns(450)
                        .aggregateConversions(380)
                        .averageConversionRate(84.4)
                        .topConvertingWorkflowId(UUID.randomUUID())
                        .topConvertingWorkflowName("Comment Lead Magnet")
                        .funnels(java.util.List.of())
                        .build();

        when(analyticsService.getFunnelsOverview(testOrgId)).thenReturn(overview);

        mockMvc.perform(get("/api/v1/analytics/funnels/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalPipelines").value(3))
                .andExpect(jsonPath("$.data.aggregateRuns").value(450))
                .andExpect(jsonPath("$.data.aggregateConversions").value(380))
                .andExpect(jsonPath("$.data.averageConversionRate").value(84.4));
    }
}
