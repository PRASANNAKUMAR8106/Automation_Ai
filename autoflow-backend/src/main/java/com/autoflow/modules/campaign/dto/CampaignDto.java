package com.autoflow.modules.campaign.dto;

import com.autoflow.modules.campaign.entity.BroadcastRecipientStatus;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.LeadStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CampaignDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCampaignRequest {
        @NotBlank(message = "Campaign name is required")
        private String name;

        @NotNull(message = "Channel type is required")
        private ChannelType channel;

        @NotBlank(message = "Message template is required")
        private String messageTemplate;

        private String mediaUrl;
        private List<String> targetTags;
        private LeadStatus targetLeadStatus;
        private int minLeadScore;

        @Builder.Default
        private boolean skipExpiredWindow = true;

        private Instant scheduledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignResponse {
        private UUID id;
        private String name;
        private ChannelType channel;
        private BroadcastCampaignStatus status;
        private String messageTemplate;
        private String mediaUrl;
        private List<String> targetTags;
        private LeadStatus targetLeadStatus;
        private int minLeadScore;
        private boolean skipExpiredWindow;
        private Instant scheduledAt;
        private Instant startedAt;
        private Instant completedAt;
        private int totalRecipients;
        private int sentCount;
        private int deliveredCount;
        private int failedCount;
        private Instant createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignRecipientResponse {
        private UUID id;
        private UUID contactId;
        private String contactName;
        private String contactUsername;
        private String contactExternalId;
        private BroadcastRecipientStatus status;
        private String errorMessage;
        private Instant sentAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CampaignDetailResponse {
        private CampaignResponse campaign;
        private List<CampaignRecipientResponse> recentRecipients;
        private double deliveryRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceEstimateRequest {
        @NotNull(message = "Channel type is required")
        private ChannelType channel;

        private List<String> targetTags;
        private LeadStatus targetLeadStatus;
        private int minLeadScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AudienceEstimateResponse {
        private int totalMatchingContacts;
        private int eligibleWindowContacts;
        private int ineligibleWindowContacts;
        private ChannelType channel;
    }
}
