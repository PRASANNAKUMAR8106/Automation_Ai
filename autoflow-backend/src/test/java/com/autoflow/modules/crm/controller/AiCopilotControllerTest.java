package com.autoflow.modules.crm.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionRequest;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionResponse;
import com.autoflow.modules.crm.service.AiCopilotService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AiCopilotController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AiCopilotController Web MVC Tests")
class AiCopilotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiCopilotService aiCopilotService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/ai-suggest returns 200 OK with suggestion response")
    void shouldGenerateAiSuggestion() throws Exception {
        AiSuggestionRequest req = AiSuggestionRequest.builder()
                .personaOverride("Enthusiastic and concise")
                .build();

        AiSuggestionResponse res = AiSuggestionResponse.builder()
                .conversationId(testConvoId)
                .suggestedReply("Hi there! Standard shipping takes 3-5 business days.")
                .confidenceScore(0.94)
                .requiresHumanHandoff(false)
                .sourceArticleTitles(List.of("Shipping Guidelines"))
                .activeModel("mock-model-v1")
                .build();

        when(aiCopilotService.generateSuggestion(eq(testOrgId), eq(testConvoId), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/crm/conversations/{id}/ai-suggest", testConvoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(testConvoId.toString()))
                .andExpect(jsonPath("$.suggestedReply").value("Hi there! Standard shipping takes 3-5 business days."))
                .andExpect(jsonPath("$.confidenceScore").value(0.94))
                .andExpect(jsonPath("$.requiresHumanHandoff").value(false))
                .andExpect(jsonPath("$.sourceArticleTitles[0]").value("Shipping Guidelines"));
    }
}
