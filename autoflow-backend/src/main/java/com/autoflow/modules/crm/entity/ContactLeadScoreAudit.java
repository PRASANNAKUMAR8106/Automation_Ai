package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "contact_lead_score_audits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactLeadScoreAudit extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id", nullable = false)
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @Column(name = "previous_score", nullable = false)
    private int previousScore;

    @Column(name = "new_score", nullable = false)
    private int newScore;

    @Column(name = "score_delta", nullable = false)
    private int scoreDelta;

    @Column(name = "reason", nullable = false)
    private String reason;
}
