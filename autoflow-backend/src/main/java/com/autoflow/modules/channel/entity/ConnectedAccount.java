package com.autoflow.modules.channel.entity;

import com.autoflow.common.TenantAwareEntity;
import com.autoflow.modules.crm.entity.ChannelType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;

/**
 * Connected social-media channel account (e.g., Instagram Professional, WhatsApp Business).
 * Stores encrypted OAuth access tokens and account metadata.
 */
@Entity
@Table(name = "connected_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_org_channel_account", columnNames = {"organization_id", "channel", "external_account_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConnectedAccount extends TenantAwareEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, columnDefinition = "channel_type")
    @org.hibernate.annotations.JdbcType(org.hibernate.dialect.PostgreSQLEnumJdbcType.class)
    private ChannelType channel;

    @Column(name = "external_account_id", nullable = false)
    private String externalAccountId;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_handle")
    private String accountHandle;

    @Column(name = "encrypted_access_token", nullable = false, columnDefinition = "TEXT")
    private String encryptedAccessToken;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private ConnectedAccountStatus status = ConnectedAccountStatus.ACTIVE;

    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private String metadata = "{}";

    public boolean isExpired() {
        return tokenExpiresAt != null && Instant.now().isAfter(tokenExpiresAt);
    }

    public boolean needsRefresh(int daysThreshold) {
        return tokenExpiresAt != null && Instant.now().plus(Duration.ofDays(daysThreshold)).isAfter(tokenExpiresAt);
    }
}
