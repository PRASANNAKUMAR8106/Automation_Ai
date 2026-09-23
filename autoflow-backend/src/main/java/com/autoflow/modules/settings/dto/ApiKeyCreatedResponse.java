package com.autoflow.modules.settings.dto;

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
public class ApiKeyCreatedResponse {
    private UUID id;
    private String name;
    private String keyPrefix;
    private String maskedKey;
    private String secretKey; // Only returned once upon creation or rotation
    private ApiKeyStatus status;
    private List<String> scopes;
    private Instant expiresAt;
    private Instant createdAt;
}
