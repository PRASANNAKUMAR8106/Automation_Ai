package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ConversationInternalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationInternalNoteRepository extends JpaRepository<ConversationInternalNote, UUID> {

    List<ConversationInternalNote> findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(UUID organizationId, UUID conversationId);
}
