package com.autoflow.modules.crm.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import com.autoflow.modules.crm.service.CsatService;
import com.autoflow.modules.crm.service.SlaMonitoringService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ConversationIntelligenceController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ConversationIntelligenceController Web MVC Tests")
class ConversationIntelligenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SlaMonitoringService slaMonitoringService;

    @MockBean
    private CsatService csatService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/crm/sla/policies returns organization policies")
    void testGetSlaPolicies() throws Exception {
        SlaPolicyResponse policy = SlaPolicyResponse.builder()
                .id(UUID.randomUUID())
                .name("WhatsApp Priority Policy")
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.HIGH)
                .firstResponseTimeSeconds(300)
                .resolutionTimeSeconds(3600)
                .routingPolicy(RoutingPolicy.LEAST_BUSY)
                .active(true)
                .build();

        when(slaMonitoringService.getPolicies(testOrgId)).thenReturn(List.of(policy));

        mockMvc.perform(get("/api/v1/crm/sla/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("WhatsApp Priority Policy"))
                .andExpect(jsonPath("$.data[0].firstResponseTimeSeconds").value(300));
    }

    @Test
    @DisplayName("GET /api/v1/crm/analytics/performance returns performance telemetry")
    void testGetPerformanceAnalytics() throws Exception {
        PerformanceAnalyticsResponse analytics = PerformanceAnalyticsResponse.builder()
                .averageFirstResponseTimeMinutes(4.2)
                .averageResolutionTimeMinutes(28.5)
                .slaFirstResponseComplianceRate(98.2)
                .slaResolutionComplianceRate(95.0)
                .totalConversations(150L)
                .resolvedConversations(130L)
                .activeConversations(20L)
                .averageCsatRating(4.9)
                .totalCsatResponses(85L)
                .aiAutoPilotDeflectionRate(64.5)
                .build();

        when(slaMonitoringService.getPerformanceAnalytics(testOrgId)).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/crm/analytics/performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.averageFirstResponseTimeMinutes").value(4.2))
                .andExpect(jsonPath("$.data.averageCsatRating").value(4.9))
                .andExpect(jsonPath("$.data.aiAutoPilotDeflectionRate").value(64.5));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/csat submits rating and feedback")
    void testSubmitCsatFeedback() throws Exception {
        SubmitCsatRequest request = SubmitCsatRequest.builder()
                .rating(5)
                .feedbackText("Super fast resolution!")
                .build();

        CsatSurveyResponse response = CsatSurveyResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(testConvoId)
                .rating(5)
                .feedbackText("Super fast resolution!")
                .status("COMPLETED")
                .respondedAt(Instant.now())
                .build();

        when(csatService.submitFeedback(eq(testOrgId), eq(testConvoId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/crm/conversations/" + testConvoId + "/csat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }
}
