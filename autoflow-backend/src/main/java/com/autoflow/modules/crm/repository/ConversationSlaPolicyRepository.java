package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.ConversationPriority;
import com.autoflow.modules.crm.entity.ConversationSlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationSlaPolicyRepository extends JpaRepository<ConversationSlaPolicy, UUID> {

    List<ConversationSlaPolicy> findAllByOrganizationIdAndActiveTrue(UUID organizationId);

    Optional<ConversationSlaPolicy> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ConversationSlaPolicy> findFirstByOrganizationIdAndChannelAndPriorityAndActiveTrue(
            UUID organizationId, ChannelType channel, ConversationPriority priority);

    Optional<ConversationSlaPolicy> findFirstByOrganizationIdAndPriorityAndActiveTrue(
            UUID organizationId, ConversationPriority priority);
}
