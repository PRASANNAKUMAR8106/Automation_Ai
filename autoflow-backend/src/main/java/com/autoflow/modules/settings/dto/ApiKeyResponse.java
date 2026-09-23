package com.autoflow.modules.settings.dto;

import com.autoflow.modules.settings.entity.ApiKey;
import com.autoflow.modules.settings.entity.ApiKeyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {
    private UUID id;
    private String name;
    private String keyPrefix;
    private String maskedKey;
    private ApiKeyStatus status;
    private List<String> scopes;
    private Instant expiresAt;
    private Instant lastUsedAt;
    private Instant createdAt;

    public static ApiKeyResponse fromEntity(ApiKey entity) {
        return ApiKeyResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .keyPrefix(entity.getKeyPrefix())
                .maskedKey(entity.getMaskedKey())
                .status(entity.getStatus())
                .scopes(entity.getScopes())
                .expiresAt(entity.getExpiresAt())
                .lastUsedAt(entity.getLastUsedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
