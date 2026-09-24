package com.autoflow.modules.campaign.entity;

import com.autoflow.common.TenantAwareEntity;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.LeadStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "broadcast_campaigns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastCampaign extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BroadcastCampaignStatus status = BroadcastCampaignStatus.DRAFT;

    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;

    @Column(name = "media_url", length = 1024)
    private String mediaUrl;

    @Column(name = "target_tags")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> targetTags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "target_lead_status")
    private LeadStatus targetLeadStatus;

    @Column(name = "min_lead_score")
    @Builder.Default
    private int minLeadScore = 0;

    @Column(name = "skip_expired_window", nullable = false)
    @Builder.Default
    private boolean skipExpiredWindow = true;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_recipients", nullable = false)
    @Builder.Default
    private int totalRecipients = 0;

    @Column(name = "sent_count", nullable = false)
    @Builder.Default
    private int sentCount = 0;

    @Column(name = "delivered_count", nullable = false)
    @Builder.Default
    private int deliveredCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;
}
