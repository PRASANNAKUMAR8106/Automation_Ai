package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "contacts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_contact_channel", columnNames = {"organization_id", "channel", "external_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Contact extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private ChannelType channel;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "username")
    private String username;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "lead_status", nullable = false)
    @Builder.Default
    private LeadStatus leadStatus = LeadStatus.NEW;

    @Column(name = "lead_score", nullable = false)
    @Builder.Default
    private int leadScore = 0;

    @Column(name = "tags")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Column(name = "custom_fields", columnDefinition = "jsonb")
    @Builder.Default
    private String customFields = "{}";

    @Column(name = "consent_given", nullable = false)
    @Builder.Default
    private boolean consentGiven = false;

    @Column(name = "consent_timestamp")
    private Instant consentTimestamp;

    @Column(name = "last_interaction_at", nullable = false)
    @Builder.Default
    private Instant lastInteractionAt = Instant.now();

    public boolean isOptedOut() {
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null) {
                    String lower = tag.trim().toLowerCase();
                    if (lower.equals("opt_out") || lower.equals("opted_out") ||
                        lower.equals("unsubscribed") || lower.equals("suppressed") ||
                        lower.equals("dnc") || lower.equals("do_not_contact") ||
                        lower.equals("stop")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isSuppressed() {
        return isOptedOut();
    }
}
