package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.ConversationInternalNote;

import java.util.List;
import java.util.UUID;

public interface InternalNoteService {

    InternalNoteResponse createInternalNote(UUID organizationId, UUID conversationId, UUID authorUserId, String authorEmail, PostInternalNoteRequest request);

    List<InternalNoteResponse> getInternalNotes(UUID organizationId, UUID conversationId);
}
