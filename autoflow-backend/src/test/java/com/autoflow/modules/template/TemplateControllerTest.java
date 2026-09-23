package com.autoflow.modules.template;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.template.controller.TemplateController;
import com.autoflow.modules.template.dto.TemplateDto.*;
import com.autoflow.modules.template.service.TemplateService;
import com.autoflow.modules.workflow.dto.WorkflowResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = TemplateController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TemplateController Web MVC Tests")
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TemplateService templateService;

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
    @DisplayName("GET /api/v1/templates returns filtered templates list")
    void testListTemplates() throws Exception {
        UUID templateId = UUID.randomUUID();
        TemplateResponse templateResp = TemplateResponse.builder()
                .id(templateId)
                .name("Comment to Lead Magnet")
                .category("LEAD_MAGNET")
                .description("Auto-reply and DM free PDF")
                .tags(List.of("instagram", "pdf"))
                .isFeatured(true)
                .createdAt(Instant.now())
                .build();

        when(templateService.listTemplates(eq("LEAD_MAGNET"), eq("pdf"), eq(true)))
                .thenReturn(List.of(templateResp));

        mockMvc.perform(get("/api/v1/templates")
                        .param("category", "LEAD_MAGNET")
                        .param("tag", "pdf")
                        .param("featured", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(templateId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Comment to Lead Magnet"))
                .andExpect(jsonPath("$.data[0].category").value("LEAD_MAGNET"));

        verify(templateService).listTemplates("LEAD_MAGNET", "pdf", true);
    }

    @Test
    @DisplayName("GET /api/v1/templates/{id} returns template details")
    void testGetTemplateById() throws Exception {
        UUID templateId = UUID.randomUUID();
        TemplateResponse templateResp = TemplateResponse.builder()
                .id(templateId)
                .name("Coupon Delivery")
                .category("ECOMMERCE")
                .graphDefinition("{\"nodes\":[],\"edges\":[]}")
                .build();

        when(templateService.getTemplateById(templateId)).thenReturn(templateResp);

        mockMvc.perform(get("/api/v1/templates/" + templateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Coupon Delivery"));
    }

    @Test
    @DisplayName("POST /api/v1/templates/{id}/instantiate deep-clones template into tenant workflow")
    void testInstantiateTemplate() throws Exception {
        UUID templateId = UUID.randomUUID();
        UUID newWorkflowId = UUID.randomUUID();

        InstantiateTemplateRequest request = InstantiateTemplateRequest.builder()
                .workflowName("My Black Friday Funnel")
                .description("Customized from coupon template")
                .build();

        WorkflowResponse workflowResponse = WorkflowResponse.builder()
                .id(newWorkflowId)
                .organizationId(testOrgId)
                .name("My Black Friday Funnel")
                .status("DRAFT")
                .activeVersionNumber(1)
                .build();

        when(templateService.instantiateTemplate(eq(testOrgId), eq(templateId), any(InstantiateTemplateRequest.class)))
                .thenReturn(workflowResponse);

        mockMvc.perform(post("/api/v1/templates/" + templateId + "/instantiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(newWorkflowId.toString()))
                .andExpect(jsonPath("$.data.organizationId").value(testOrgId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        verify(templateService).instantiateTemplate(eq(testOrgId), eq(templateId), any(InstantiateTemplateRequest.class));
    }
}
