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
}
