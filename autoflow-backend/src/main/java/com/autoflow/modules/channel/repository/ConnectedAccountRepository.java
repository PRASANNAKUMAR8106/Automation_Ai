package com.autoflow.modules.channel.repository;

import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.crm.entity.ChannelType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConnectedAccountRepository extends JpaRepository<ConnectedAccount, UUID> {

    List<ConnectedAccount> findByOrganizationId(UUID organizationId);

    List<ConnectedAccount> findByOrganizationIdAndStatus(UUID organizationId, ConnectedAccountStatus status);

    Optional<ConnectedAccount> findByOrganizationIdAndChannel(UUID organizationId, ChannelType channel);

    Optional<ConnectedAccount> findByOrganizationIdAndChannelAndExternalAccountId(
            UUID organizationId,
            ChannelType channel,
            String externalAccountId
    );

    Optional<ConnectedAccount> findByChannelAndExternalAccountId(ChannelType channel, String externalAccountId);

    List<ConnectedAccount> findByTokenExpiresAtBeforeAndStatus(Instant threshold, ConnectedAccountStatus status);

    long countByOrganizationIdAndStatus(UUID organizationId, ConnectedAccountStatus status);
}
