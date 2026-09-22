package com.autoflow.modules.channel.dto;

import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.crm.entity.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedAccountResponse {
    private UUID id;
    private UUID organizationId;
    private ChannelType channel;
    private String externalAccountId;
    private String accountName;
    private String accountHandle;
    private ConnectedAccountStatus status;
    private Instant tokenExpiresAt;
    private boolean isExpired;
    private Instant createdAt;

    public static ConnectedAccountResponse fromEntity(ConnectedAccount entity) {
        return ConnectedAccountResponse.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganizationId())
                .channel(entity.getChannel())
                .externalAccountId(entity.getExternalAccountId())
                .accountName(entity.getAccountName())
                .accountHandle(entity.getAccountHandle())
                .status(entity.getStatus())
                .tokenExpiresAt(entity.getTokenExpiresAt())
                .isExpired(entity.isExpired())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
