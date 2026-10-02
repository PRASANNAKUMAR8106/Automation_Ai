package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ConversationSlaEvent;
import com.autoflow.modules.crm.entity.ConversationSlaEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationSlaEventRepository extends JpaRepository<ConversationSlaEvent, UUID> {

    List<ConversationSlaEvent> findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(UUID organizationId, UUID conversationId);

    boolean existsByConversationIdAndEventType(UUID conversationId, ConversationSlaEventType eventType);

    long countByOrganizationIdAndEventType(UUID organizationId, ConversationSlaEventType eventType);
}
