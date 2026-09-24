package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    Optional<Contact> findByOrganizationIdAndChannelAndExternalId(UUID organizationId, ChannelType channel, String externalId);

    List<Contact> findByOrganizationId(UUID organizationId);

    List<Contact> findByOrganizationIdAndChannel(UUID organizationId, ChannelType channel);

    long countByOrganizationId(UUID organizationId);
}
