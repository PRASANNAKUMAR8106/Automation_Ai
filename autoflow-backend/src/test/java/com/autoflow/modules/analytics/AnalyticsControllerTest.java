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
}
