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

        InternalNoteResponse response = toResponse(saved);

        // Broadcast to live chat operators viewing the conversation
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
        // Assert conversation exists and belongs to tenant
        conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        return internalNoteRepository.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(organizationId, conversationId)
                .stream()
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
