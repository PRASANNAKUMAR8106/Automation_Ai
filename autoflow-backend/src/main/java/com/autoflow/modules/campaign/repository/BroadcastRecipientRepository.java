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

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE BroadcastRecipient r SET r.status = com.autoflow.modules.campaign.entity.BroadcastRecipientStatus.PROCESSING " +
           "WHERE r.id = :recipientId AND r.status = com.autoflow.modules.campaign.entity.BroadcastRecipientStatus.PENDING")
    int claimRecipientForProcessing(@org.springframework.data.repository.query.Param("recipientId") UUID recipientId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE BroadcastRecipient r SET r.status = com.autoflow.modules.campaign.entity.BroadcastRecipientStatus.CANCELLED, " +
           "r.errorMessage = 'Campaign was cancelled before dispatch.' " +
           "WHERE r.campaign.id = :campaignId AND (r.status = com.autoflow.modules.campaign.entity.BroadcastRecipientStatus.PENDING OR r.status = com.autoflow.modules.campaign.entity.BroadcastRecipientStatus.PROCESSING)")
    int cancelPendingRecipients(@org.springframework.data.repository.query.Param("campaignId") UUID campaignId);
}
