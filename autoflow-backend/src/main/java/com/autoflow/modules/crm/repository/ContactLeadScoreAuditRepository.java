package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ContactLeadScoreAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ContactLeadScoreAuditRepository extends JpaRepository<ContactLeadScoreAudit, UUID> {

    List<ContactLeadScoreAudit> findAllByOrganizationIdAndContactIdOrderByCreatedAtDesc(UUID organizationId, UUID contactId);

    List<ContactLeadScoreAudit> findAllByOrganizationIdAndConversationIdOrderByCreatedAtDesc(UUID organizationId, UUID conversationId);
}
