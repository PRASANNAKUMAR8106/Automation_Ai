package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByOrganizationIdAndContactIdAndChannel(UUID organizationId, UUID contactId, ChannelType channel);

    List<Conversation> findByOrganizationIdOrderByLastMessageAtDesc(UUID organizationId);
}
