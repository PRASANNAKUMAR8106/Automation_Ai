package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import com.autoflow.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "csat_surveys")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsatSurvey extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @Column(name = "rating")
    private Integer rating; // 1 to 5 scale

    @Column(name = "feedback_text", columnDefinition = "text")
    private String feedbackText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CsatStatus status = CsatStatus.DISPATCHED;

    @Column(name = "dispatched_at", nullable = false)
    @Builder.Default
    private Instant dispatchedAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;
}
