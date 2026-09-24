package com.autoflow.modules.campaign.scheduler;

import com.autoflow.modules.campaign.service.CampaignService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "autoflow.campaigns.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class CampaignScheduler {

    private final CampaignService campaignService;

    @Scheduled(fixedDelay = 30000)
    public void schedulePendingCampaigns() {
        try {
            campaignService.processScheduledCampaigns();
        } catch (Exception e) {
            log.error("Error executing campaign scheduler: {}", e.getMessage(), e);
        }
    }
}
