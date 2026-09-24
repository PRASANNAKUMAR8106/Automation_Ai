package com.autoflow.modules.campaign.service;

import com.autoflow.modules.campaign.dto.CampaignDto.*;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CampaignService {

    AudienceEstimateResponse estimateAudience(UUID organizationId, AudienceEstimateRequest request);

    CampaignResponse createCampaign(UUID organizationId, CreateCampaignRequest request);

    Page<CampaignResponse> getCampaigns(UUID organizationId, BroadcastCampaignStatus status, Pageable pageable);

    CampaignDetailResponse getCampaignDetail(UUID organizationId, UUID campaignId);

    CampaignResponse cancelCampaign(UUID organizationId, UUID campaignId);

    void processScheduledCampaigns();

    void executeCampaign(UUID campaignId);
}
