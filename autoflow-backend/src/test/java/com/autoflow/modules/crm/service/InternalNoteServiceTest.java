package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationInternalNote;
import com.autoflow.modules.crm.repository.ConversationInternalNoteRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.impl.InternalNoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InternalNoteService Unit Tests")
class InternalNoteServiceTest {

    @Mock
    private ConversationInternalNoteRepository internalNoteRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private LiveChatStreamService liveChatStreamService;

    private InternalNoteServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InternalNoteServiceImpl(internalNoteRepository, conversationRepository, liveChatStreamService);
    }

    @Test
    @DisplayName("Should create internal note and broadcast to active stream subscribers")
    void shouldCreateInternalNoteAndBroadcast() {
        Conversation conversation = Conversation.builder().build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));
        when(internalNoteRepository.save(any(ConversationInternalNote.class))).thenAnswer(i -> {
            ConversationInternalNote saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        PostInternalNoteRequest req = PostInternalNoteRequest.builder()
                .content("Checking with supervisor on VIP discount.")
                .noteType("INTERNAL_NOTE")
                .build();

        InternalNoteResponse result = service.createInternalNote(orgId, convoId, authorId, "agent@autoflow.ai", req);

        assertThat(result.getContent()).isEqualTo("Checking with supervisor on VIP discount.");
        assertThat(result.getNoteType()).isEqualTo("INTERNAL_NOTE");
        assertThat(result.getAuthorEmail()).isEqualTo("agent@autoflow.ai");

        verify(internalNoteRepository).save(any(ConversationInternalNote.class));
        verify(liveChatStreamService).broadcastInternalNote(eq(orgId), eq(convoId), any(InternalNoteResponse.class));
    }

    @Test
    @DisplayName("Should create supervisor whisper note")
    void shouldCreateSupervisorWhisper() {
        Conversation conversation = Conversation.builder().build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));
        when(internalNoteRepository.save(any(ConversationInternalNote.class))).thenAnswer(i -> {
            ConversationInternalNote saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        PostInternalNoteRequest req = PostInternalNoteRequest.builder()
                .content("Offer them 15% off annual tier.")
                .noteType("SUPERVISOR_WHISPER")
                .build();

        InternalNoteResponse result = service.createInternalNote(orgId, convoId, authorId, "lead@autoflow.ai", req);

        assertThat(result.getNoteType()).isEqualTo("SUPERVISOR_WHISPER");
    }

    @Test
    @DisplayName("Should enforce tenant boundary when fetching notes")
    void shouldEnforceTenantBoundary() {
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInternalNotes(orgId, convoId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should reject internal note content attempting external outbound dispatch")
    void shouldRejectOutboundInInternalNote() {
        PostInternalNoteRequest req = PostInternalNoteRequest.builder()
                .content("[OUTBOUND] Direct message to customer")
                .noteType("INTERNAL_NOTE")
                .build();

        assertThatThrownBy(() -> service.createInternalNote(orgId, convoId, authorId, "agent@autoflow.ai", req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("FIREWALL BREACH");
    }

    @Test
    @DisplayName("Should enforce visibility filter: non-supervisors cannot view whispers by other authors")
    void shouldEnforceWhisperVisibility() {
        Conversation conversation = Conversation.builder().build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));

        UUID supervisorId = UUID.randomUUID();
        UUID otherAgentId = UUID.randomUUID();

        ConversationInternalNote teamNote = ConversationInternalNote.builder()
                .conversation(conversation)
                .noteType("INTERNAL_NOTE")
                .authorUserId(authorId)
                .authorEmail("agent@autoflow.ai")
                .content("General team note")
                .build();
        teamNote.setId(UUID.randomUUID());

        ConversationInternalNote whisper = ConversationInternalNote.builder()
                .conversation(conversation)
                .noteType("SUPERVISOR_WHISPER")
                .authorUserId(supervisorId)
                .authorEmail("lead@autoflow.ai")
                .content("Confidential lead whisper")
                .build();
        whisper.setId(UUID.randomUUID());

        when(internalNoteRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(orgId, convoId))
                .thenReturn(List.of(teamNote, whisper));

        // When supervisor views: sees both
        List<InternalNoteResponse> supervisorView = service.getInternalNotes(orgId, convoId, otherAgentId, true);
        assertThat(supervisorView).hasSize(2);

        // When non-supervisor views: sees only teamNote
        List<InternalNoteResponse> agentView = service.getInternalNotes(orgId, convoId, otherAgentId, false);
        assertThat(agentView).hasSize(1);
        assertThat(agentView.get(0).getContent()).isEqualTo("General team note");

        // When author of whisper views as non-supervisor: sees their own whisper
        List<InternalNoteResponse> authorView = service.getInternalNotes(orgId, convoId, supervisorId, false);
        assertThat(authorView).hasSize(2);
    }
}
