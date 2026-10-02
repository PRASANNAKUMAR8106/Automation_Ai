package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "conversation_sla_policies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSlaPolicy extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel")
    private ChannelType channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private ConversationPriority priority = ConversationPriority.NORMAL;

    @Column(name = "first_response_time_seconds", nullable = false)
    @Builder.Default
    private int firstResponseTimeSeconds = 900; // 15 minutes default

    @Column(name = "resolution_time_seconds", nullable = false)
    @Builder.Default
    private int resolutionTimeSeconds = 7200; // 2 hours default

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_policy", nullable = false)
    @Builder.Default
    private RoutingPolicy routingPolicy = RoutingPolicy.LEAST_BUSY;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "whatsapp_template_enabled", nullable = false)
    @Builder.Default
    private boolean whatsappTemplateEnabled = false;
}
