package com.autoflow.modules.crm.service.impl;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationInternalNote;
import com.autoflow.modules.crm.repository.ConversationInternalNoteRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.InternalNoteService;
import com.autoflow.modules.crm.service.LiveChatStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternalNoteServiceImpl implements InternalNoteService {

    private final ConversationInternalNoteRepository internalNoteRepository;
    private final ConversationRepository conversationRepository;
    private final LiveChatStreamService liveChatStreamService;

    @Override
    @Transactional
    public InternalNoteResponse createInternalNote(UUID organizationId, UUID conversationId, UUID authorUserId, String authorEmail, PostInternalNoteRequest request) {
        // Multi-layer social provider firewall: internal notes and supervisor whispers must NEVER reach external social channels
        if (request.getContent() != null && (request.getContent().startsWith("[OUTBOUND]") || request.getContent().startsWith("[DISPATCH_EXTERNAL]"))) {
            throw new SecurityException("FIREWALL BREACH: Internal notes and supervisor whispers cannot be routed to external social channels");
        }

        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        String rawType = request.getNoteType();
        String noteType = (rawType != null && rawType.equalsIgnoreCase("SUPERVISOR_WHISPER"))
                ? "SUPERVISOR_WHISPER"
                : "INTERNAL_NOTE";

        ConversationInternalNote note = ConversationInternalNote.builder()
                .conversation(conversation)
                .authorUserId(authorUserId)
                .authorEmail(authorEmail)
                .noteType(noteType)
                .content(request.getContent().trim())
                .build();
        note.setOrganizationId(organizationId);

        ConversationInternalNote saved = internalNoteRepository.save(note);

        // Durable, tamper-evident audit checksum. The saved instance is managed inside this
        // transaction, so sealing it here is flushed to the database on commit.
        com.autoflow.modules.crm.service.AuditChecksumUtils.sealInternalNote(organizationId, conversation.getId(), saved);

        InternalNoteResponse response = toResponse(saved);

        // Broadcast to live chat operators viewing the conversation (internal SSE only, zero external social provider dispatch)
        try {
            liveChatStreamService.broadcastInternalNote(organizationId, conversationId, response);
        } catch (Exception e) {
            log.warn("Failed broadcasting internal note for conversation {}: {}", conversationId, e.getMessage());
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalNoteResponse> getInternalNotes(UUID organizationId, UUID conversationId) {
        return getInternalNotes(organizationId, conversationId, null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalNoteResponse> getInternalNotes(UUID organizationId, UUID conversationId, UUID requestingUserId, boolean isSupervisorOrAdmin) {
        // Assert conversation exists and belongs to tenant
        conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        List<ConversationInternalNote> allNotes = internalNoteRepository
                .findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(organizationId, conversationId);

        return allNotes.stream()
                .filter(n -> {
                    // INTERNAL_NOTE = permitted internal team visibility
                    if (!"SUPERVISOR_WHISPER".equalsIgnoreCase(n.getNoteType())) {
                        return true;
                    }
                    // SUPERVISOR_WHISPER = explicitly authorized supervisor/agent visibility
                    if (isSupervisorOrAdmin) {
                        return true;
                    }
                    return requestingUserId != null && requestingUserId.equals(n.getAuthorUserId());
                })
                .map(this::toResponse)
                .toList();
    }

    private InternalNoteResponse toResponse(ConversationInternalNote n) {
        return InternalNoteResponse.builder()
                .id(n.getId())
                .conversationId(n.getConversation().getId())
                .authorUserId(n.getAuthorUserId())
                .authorEmail(n.getAuthorEmail())
                .noteType(n.getNoteType())
                .content(n.getContent())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
