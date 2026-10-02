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

    Optional<Conversation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<Conversation> findByOrganizationIdOrderByLastMessageAtDesc(UUID organizationId);

    List<Conversation> findAllByOrganizationId(UUID organizationId);

    Long countByOrganizationIdAndAssignedUserIdAndResolvedFalse(UUID organizationId, UUID assignedUserId);

    Long countByOrganizationIdAndResolved(UUID organizationId, boolean resolved);

    Long countByOrganizationId(UUID organizationId);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Conversation c WHERE c.resolved = false AND c.firstAgentReplyAt IS NULL AND c.slaFirstResponseDueAt IS NOT NULL AND c.slaFirstResponseDueAt < :now AND c.slaFirstResponseBreached = false")
    List<Conversation> findPendingFirstResponseBreaches(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM Conversation c WHERE c.resolved = false AND c.slaResolutionDueAt IS NOT NULL AND c.slaResolutionDueAt < :now AND c.slaResolutionBreached = false")
    List<Conversation> findPendingResolutionBreaches(@org.springframework.data.repository.query.Param("now") java.time.Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Conversation c SET c.slaFirstResponseBreached = true, c.priority = com.autoflow.modules.crm.entity.ConversationPriority.URGENT WHERE c.id = :id AND c.slaFirstResponseBreached = false")
    int markFirstResponseBreachedAtomic(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE Conversation c SET c.slaResolutionBreached = true, c.priority = com.autoflow.modules.crm.entity.ConversationPriority.URGENT WHERE c.id = :id AND c.slaResolutionBreached = false")
    int markResolutionBreachedAtomic(@org.springframework.data.repository.query.Param("id") UUID id);
}
