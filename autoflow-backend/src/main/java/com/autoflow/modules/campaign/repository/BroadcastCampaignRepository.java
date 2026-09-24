package com.autoflow.modules.campaign.repository;

import com.autoflow.modules.campaign.entity.BroadcastCampaign;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BroadcastCampaignRepository extends JpaRepository<BroadcastCampaign, UUID> {

    Optional<BroadcastCampaign> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<BroadcastCampaign> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<BroadcastCampaign> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, BroadcastCampaignStatus status, Pageable pageable);

    List<BroadcastCampaign> findByStatusAndScheduledAtLessThanEqual(BroadcastCampaignStatus status, Instant now);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE BroadcastCampaign c SET c.status = com.autoflow.modules.campaign.entity.BroadcastCampaignStatus.RUNNING, c.startedAt = :now " +
           "WHERE c.id = :id AND c.status = com.autoflow.modules.campaign.entity.BroadcastCampaignStatus.SCHEDULED")
    int claimCampaignForExecution(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("now") Instant now);

    @org.springframework.data.jpa.repository.Query("SELECT c.status FROM BroadcastCampaign c WHERE c.id = :id")
    Optional<BroadcastCampaignStatus> findStatusById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE BroadcastCampaign c SET c.status = com.autoflow.modules.campaign.entity.BroadcastCampaignStatus.CANCELLED " +
           "WHERE c.id = :id AND c.organizationId = :organizationId AND " +
           "(c.status = com.autoflow.modules.campaign.entity.BroadcastCampaignStatus.SCHEDULED OR c.status = com.autoflow.modules.campaign.entity.BroadcastCampaignStatus.RUNNING)")
    int cancelCampaignAtomically(@org.springframework.data.repository.query.Param("organizationId") UUID organizationId, @org.springframework.data.repository.query.Param("id") UUID id);
}
