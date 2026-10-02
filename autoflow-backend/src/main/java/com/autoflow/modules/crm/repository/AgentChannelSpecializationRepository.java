package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.AgentChannelSpecialization;
import com.autoflow.modules.crm.entity.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AgentChannelSpecializationRepository extends JpaRepository<AgentChannelSpecialization, UUID> {

    List<AgentChannelSpecialization> findAllByOrganizationIdAndChannelAndActiveTrue(UUID organizationId, ChannelType channel);

    List<AgentChannelSpecialization> findAllByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<AgentChannelSpecialization> findByOrganizationIdAndUserIdAndChannel(UUID organizationId, UUID userId, ChannelType channel);
}
