package com.autoflow.modules.crm.controller;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationInternalNote;
import com.autoflow.modules.crm.repository.*;
import com.autoflow.modules.crm.service.*;
import com.autoflow.modules.crm.service.impl.CannedResponseServiceImpl;
import com.autoflow.modules.crm.service.impl.InternalNoteServiceImpl;
import com.autoflow.modules.user.repository.UserRepository;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AgentProductivityController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Phase 22 Agent Productivity Multi-Tenant Security & Social Provider Firewall Tests")
class AgentProductivitySecurityTest {

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

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID foreignConvoId = UUID.randomUUID();
    private final UUID foreignMacroId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(tenantA);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Cross-tenant note access must be rejected with 404/ResourceNotFoundException")
    void shouldRejectCrossTenantNoteAccess() throws Exception {
        when(internalNoteService.getInternalNotes(eq(tenantA), eq(foreignConvoId), any(), any(Boolean.class)))
                .thenThrow(new ResourceNotFoundException("Conversation", foreignConvoId));

        mockMvc.perform(get("/api/v1/crm/conversations/" + foreignConvoId + "/notes"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant macro execution must fail with 404 when macro belongs to different tenant")
    void shouldRejectCrossTenantMacroExecution() throws Exception {
        when(macroExecutionService.applyMacro(eq(tenantA), any(), eq(foreignMacroId), any(), any()))
                .thenThrow(new ResourceNotFoundException("CrmMacro", foreignMacroId));

        mockMvc.perform(post("/api/v1/crm/conversations/" + foreignConvoId + "/apply-macro/" + foreignMacroId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant timeline retrieval must be rejected with 404")
    void shouldRejectCrossTenantTimelineRetrieval() throws Exception {
        when(conversationTimelineService.getConversationTimeline(eq(tenantA), eq(foreignConvoId)))
                .thenThrow(new ResourceNotFoundException("Conversation", foreignConvoId));

        mockMvc.perform(get("/api/v1/crm/conversations/" + foreignConvoId + "/timeline"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Cross-tenant canned response deletion must be rejected with 404")
    void shouldRejectCrossTenantCannedResponseDeletion() throws Exception {
        UUID snippetId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ResourceNotFoundException("CannedResponse", snippetId))
                .when(cannedResponseService).deleteCannedResponse(eq(tenantA), eq(snippetId));

        mockMvc.perform(delete("/api/v1/crm/canned-responses/" + snippetId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Supervisor whisper visibility: non-supervisors cannot view other supervisors' whispers")
    void shouldEnforceSupervisorWhisperVisibilityIsolation() {
        ConversationInternalNoteRepository noteRepo = org.mockito.Mockito.mock(ConversationInternalNoteRepository.class);
        ConversationRepository convRepo = org.mockito.Mockito.mock(ConversationRepository.class);
        LiveChatStreamService liveChat = org.mockito.Mockito.mock(LiveChatStreamService.class);

        InternalNoteServiceImpl noteService = new InternalNoteServiceImpl(noteRepo, convRepo, liveChat);

        Conversation conv = Conversation.builder().build();
        conv.setId(foreignConvoId);
        when(convRepo.findByIdAndOrganizationId(foreignConvoId, tenantA)).thenReturn(Optional.of(conv));

        UUID authorSupervisor = UUID.randomUUID();
        UUID queryingAgent = UUID.randomUUID();

        ConversationInternalNote publicNote = ConversationInternalNote.builder()
                .conversation(conv)
                .authorUserId(authorSupervisor)
                .authorEmail("sup@autoflow.ai")
                .noteType("INTERNAL_NOTE")
                .content("General internal note")
                .build();
        publicNote.setId(UUID.randomUUID());

        ConversationInternalNote whisper = ConversationInternalNote.builder()
                .conversation(conv)
                .authorUserId(authorSupervisor)
                .authorEmail("sup@autoflow.ai")
                .noteType("SUPERVISOR_WHISPER")
                .content("Restricted supervisor whisper")
                .build();
        whisper.setId(UUID.randomUUID());

        when(noteRepo.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(tenantA, foreignConvoId))
                .thenReturn(List.of(publicNote, whisper));

        // 1. Querying as non-supervisor agent (isSupervisorOrAdmin = false)
        List<InternalNoteResponse> agentView = noteService.getInternalNotes(tenantA, foreignConvoId, queryingAgent, false);
        assertThat(agentView).hasSize(1);
        assertThat(agentView.get(0).getNoteType()).isEqualTo("INTERNAL_NOTE");

        // 2. Querying as author agent (isSupervisorOrAdmin = false, but authorUserId matches)
        List<InternalNoteResponse> authorView = noteService.getInternalNotes(tenantA, foreignConvoId, authorSupervisor, false);
        assertThat(authorView).hasSize(2);

        // 3. Querying as supervisor (isSupervisorOrAdmin = true)
        List<InternalNoteResponse> supervisorView = noteService.getInternalNotes(tenantA, foreignConvoId, queryingAgent, true);
        assertThat(supervisorView).hasSize(2);
    }
}
