package com.autoflow.modules.campaign.service;

import com.autoflow.modules.campaign.dto.CampaignDto.*;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service managing omnichannel scheduled broadcast campaigns and audience segmentation.
 *
 * <h3>Channel-Specific Delivery Semantics</h3>
 * <ul>
 *   <li><b>Telegram (ChannelType.TELEGRAM):</b> At-most-once delivery model with subscriber verification.
 *       Telegram Bot API uses chat-based subscription without 24-hour decay. Local recipient atomic claiming
 *       locks in-flight dispatches. In crash recovery scenarios where provider confirmation is absent, blind
 *       retries are suppressed to prevent user spam.</li>
 *   <li><b>WhatsApp Cloud API (ChannelType.WHATSAPP):</b> At-most-once delivery model for outbound broadcast outreach.
 *       Enforces Meta's 24-hour user-initiated customer care session window. External message dispatch returns a
 *       Meta {@code wamid}, with subsequent status receipts (sent, delivered, read) streaming asynchronously via webhooks.
 *       Stale in-flight claims are transitioned to {@code FAILED_CRASH_RECOVERY} to protect Meta quality rating.</li>
 *   <li><b>Instagram Messaging API (ChannelType.INSTAGRAM):</b> At-most-once delivery model for marketing broadcasts.
 *       Enforces Meta's 24-hour messaging window. Automated marketing campaigns cannot utilize the 7-day human agent
 *       escalation tag per Meta Developer Policy. Deduplication and lease locks guarantee single external dispatch.</li>
 * </ul>
 */
public interface CampaignService {

    AudienceEstimateResponse estimateAudience(UUID organizationId, AudienceEstimateRequest request);

    CampaignResponse createCampaign(UUID organizationId, CreateCampaignRequest request);

    Page<CampaignResponse> getCampaigns(UUID organizationId, BroadcastCampaignStatus status, Pageable pageable);

    CampaignDetailResponse getCampaignDetail(UUID organizationId, UUID campaignId);

    CampaignResponse cancelCampaign(UUID organizationId, UUID campaignId);

    void processScheduledCampaigns();

    void executeCampaign(UUID campaignId);

    /**
     * Inspects in-flight recipients in PROCESSING status whose lease has expired
     * (e.g. due to JVM crash or worker termination), recovering or safely closing them
     * without generating duplicate external messages.
     *
     * @param leaseTimeout Maximum allowable processing duration before considering an attempt stale
     * @return Number of stale recipient claims recovered
     */
    int recoverStaleProcessingClaims(java.time.Duration leaseTimeout);
}
