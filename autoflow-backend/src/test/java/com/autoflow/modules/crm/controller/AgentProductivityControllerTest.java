package com.autoflow.modules.crm.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.CannedResponse;
import com.autoflow.modules.crm.entity.CrmMacro;
import com.autoflow.modules.crm.service.*;
import com.autoflow.modules.user.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AgentProductivityController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AgentProductivityController Web MVC Tests")
class AgentProductivityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CannedResponseService cannedResponseService;

    @MockBean
    private AgentCollisionService agentCollisionService;

    @MockBean
    private InternalNoteService internalNoteService;

    @MockBean
    private MacroExecutionService macroExecutionService;

    @MockBean
    private ConversationTimelineService conversationTimelineService;

    @MockBean
    private UserRepository userRepository;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testConvoId = UUID.randomUUID();
    private final UUID testMacroId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/crm/canned-responses - returns list")
    void shouldGetCannedResponses() throws Exception {
        CannedResponse snippet = CannedResponse.builder()
                .shortcut("#refund")
                .title("Refund Policy")
                .content("Refunds within 14 days")
                .category("BILLING")
                .isShared(true)
                .usageCount(5)
                .build();
        snippet.setId(UUID.randomUUID());

        when(cannedResponseService.getCannedResponses(eq(testOrgId), any(), any()))
                .thenReturn(List.of(snippet));

        mockMvc.perform(get("/api/v1/crm/canned-responses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].shortcut").value("#refund"))
                .andExpect(jsonPath("$.data[0].title").value("Refund Policy"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/canned-responses - creates canned response")
    void shouldCreateCannedResponse() throws Exception {
        CannedResponseRequest req = CannedResponseRequest.builder()
                .shortcut("#hours")
                .title("Office Hours")
                .content("Mon-Fri 9-5")
                .category("GENERAL")
                .build();

        CannedResponse created = CannedResponse.builder()
                .shortcut("#hours")
                .title("Office Hours")
                .content("Mon-Fri 9-5")
                .category("GENERAL")
                .build();
        created.setId(UUID.randomUUID());

        when(cannedResponseService.createCannedResponse(eq(testOrgId), any(), any(CannedResponseRequest.class)))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/crm/canned-responses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shortcut").value("#hours"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/interpolate-template - interpolates variables")
    void shouldInterpolateTemplate() throws Exception {
        InterpolateRequest req = InterpolateRequest.builder()
                .content("Hello {{contact.name}}!")
                .build();

        when(cannedResponseService.interpolateTemplate(eq(testOrgId), eq(testConvoId), eq("Hello {{contact.name}}!"), any()))
                .thenReturn("Hello Alex!");

        mockMvc.perform(post("/api/v1/crm/conversations/" + testConvoId + "/interpolate-template")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.interpolatedContent").value("Hello Alex!"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/presence - records agent presence")
    void shouldRecordPresence() throws Exception {
        AgentPresenceHeartbeatRequest req = AgentPresenceHeartbeatRequest.builder()
                .action("VIEWING")
                .userEmail("sarah@autoflow.ai")
                .build();

        AgentPresenceDto dto = AgentPresenceDto.builder()
                .userId(UUID.randomUUID())
                .userEmail("sarah@autoflow.ai")
                .action("VIEWING")
                .lastActiveAt(Instant.now())
                .build();

        when(agentCollisionService.recordPresence(eq(testOrgId), eq(testConvoId), any(), eq("sarah@autoflow.ai"), eq("VIEWING")))
                .thenReturn(List.of(dto));

        mockMvc.perform(post("/api/v1/crm/conversations/" + testConvoId + "/presence")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userEmail").value("sarah@autoflow.ai"))
                .andExpect(jsonPath("$.data[0].action").value("VIEWING"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/notes - creates internal note")
    void shouldCreateInternalNote() throws Exception {
        PostInternalNoteRequest req = PostInternalNoteRequest.builder()
                .content("Customer is a VIP influencer.")
                .noteType("INTERNAL_NOTE")
                .build();

        InternalNoteResponse note = InternalNoteResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(testConvoId)
                .authorEmail("agent@autoflow.ai")
                .noteType("INTERNAL_NOTE")
                .content("Customer is a VIP influencer.")
                .createdAt(Instant.now())
                .build();

        when(internalNoteService.createInternalNote(eq(testOrgId), eq(testConvoId), any(), any(), any()))
                .thenReturn(note);

        mockMvc.perform(post("/api/v1/crm/conversations/" + testConvoId + "/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("Customer is a VIP influencer."))
                .andExpect(jsonPath("$.data.noteType").value("INTERNAL_NOTE"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/apply-macro/{macroId} - applies macro atomically")
    void shouldApplyMacro() throws Exception {
        ApplyMacroResult result = ApplyMacroResult.builder()
                .macroId(testMacroId)
                .macroName("Fast Close")
                .success(true)
                .actionsExecuted(List.of("SEND_REPLY: Thanks!", "RESOLVE: true"))
                .windowExpiredSkipped(false)
                .suppressionSkipped(false)
                .build();

        when(macroExecutionService.applyMacro(eq(testOrgId), eq(testConvoId), eq(testMacroId), any(), any()))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/crm/conversations/" + testConvoId + "/apply-macro/" + testMacroId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.macroName").value("Fast Close"));
    }

    @Test
    @DisplayName("GET /api/v1/crm/conversations/{id}/timeline - returns unified timeline")
    void shouldGetTimeline() throws Exception {
        TimelineEventDto event = TimelineEventDto.builder()
                .id(UUID.randomUUID())
                .category("MESSAGE")
                .eventType("INBOUND")
                .summary("Hi there!")
                .actor("CONTACT")
                .timestamp(Instant.now())
                .build();

        when(conversationTimelineService.getConversationTimeline(eq(testOrgId), eq(testConvoId)))
                .thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/crm/conversations/" + testConvoId + "/timeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("MESSAGE"))
                .andExpect(jsonPath("$.data[0].summary").value("Hi there!"));
    }
}
