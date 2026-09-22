package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndDirection(UUID organizationId, String direction);
}
