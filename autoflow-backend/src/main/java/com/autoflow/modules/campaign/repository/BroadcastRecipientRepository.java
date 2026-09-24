package com.autoflow.modules.campaign.repository;

import com.autoflow.modules.campaign.entity.BroadcastRecipient;
import com.autoflow.modules.campaign.entity.BroadcastRecipientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BroadcastRecipientRepository extends JpaRepository<BroadcastRecipient, UUID> {

    Page<BroadcastRecipient> findByCampaignId(UUID campaignId, Pageable pageable);

    List<BroadcastRecipient> findByCampaignIdAndStatus(UUID campaignId, BroadcastRecipientStatus status);

    long countByCampaignIdAndStatus(UUID campaignId, BroadcastRecipientStatus status);

    long countByCampaignId(UUID campaignId);
}
