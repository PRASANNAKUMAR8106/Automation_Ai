package com.autoflow.modules.crm.entity;

import com.autoflow.common.BaseEntity;
import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ChannelType channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @Column(name = "last_message_at", nullable = false)
    @Builder.Default
    private Instant lastMessageAt = Instant.now();

    @Column(name = "last_customer_message_at")
    private Instant lastCustomerMessageAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private ConversationPriority priority = ConversationPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", nullable = false)
    @Builder.Default
    private ConversationSentiment sentiment = ConversationSentiment.NEUTRAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sla_policy_id")
    private ConversationSlaPolicy slaPolicy;

    @Column(name = "sla_first_response_due_at")
    private Instant slaFirstResponseDueAt;

    @Column(name = "sla_resolution_due_at")
    private Instant slaResolutionDueAt;

    @Column(name = "sla_first_response_breached", nullable = false)
    @Builder.Default
    private boolean slaFirstResponseBreached = false;

    @Column(name = "sla_resolution_breached", nullable = false)
    @Builder.Default
    private boolean slaResolutionBreached = false;

    @Column(name = "first_agent_reply_at")
    private Instant firstAgentReplyAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "ai_handled", nullable = false)
    @Builder.Default
    private boolean aiHandled = false;

    @Column(name = "human_agent_replied", nullable = false)
    @Builder.Default
    private boolean humanAgentReplied = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalated_to_user_id")
    private com.autoflow.modules.user.entity.User escalatedToUser;

    @Column(name = "escalated_at")
    private Instant escalatedAt;
}
