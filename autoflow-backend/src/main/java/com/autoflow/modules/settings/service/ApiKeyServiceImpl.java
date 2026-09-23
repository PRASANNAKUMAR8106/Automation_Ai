package com.autoflow.modules.settings.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.settings.dto.ApiKeyCreatedResponse;
import com.autoflow.modules.settings.dto.ApiKeyResponse;
import com.autoflow.modules.settings.dto.CreateApiKeyRequest;
import com.autoflow.modules.settings.entity.ApiKey;
import com.autoflow.modules.settings.entity.ApiKeyStatus;
import com.autoflow.modules.settings.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public ApiKeyCreatedResponse createApiKey(UUID orgId, CreateApiKeyRequest request) {
        String rawSecret = generateRawSecret();
        String hashed = hashSecret(rawSecret);
        String prefix = rawSecret.substring(0, 14); // e.g. "af_live_a1b2c3"
        String suffix = rawSecret.substring(rawSecret.length() - 4);
        String masked = prefix + "••••••••••••" + suffix;

        Instant expiresAt = null;
        if (request.getExpiresInDays() != null && request.getExpiresInDays() > 0) {
            expiresAt = Instant.now().plus(Duration.ofDays(request.getExpiresInDays()));
        }

        ApiKey apiKey = ApiKey.builder()
                .name(request.getName().trim())
                .keyPrefix(prefix)
                .hashedSecret(hashed)
                .maskedKey(masked)
                .scopes(request.getScopes() != null ? request.getScopes() : List.of("all"))
                .status(ApiKeyStatus.ACTIVE)
                .expiresAt(expiresAt)
                .build();
        apiKey.setOrganizationId(orgId);

        ApiKey saved = apiKeyRepository.save(apiKey);

        log.info("Generated new API key ID={} for org={}", saved.getId(), orgId);

        return ApiKeyCreatedResponse.builder()
                .id(saved.getId())
                .name(saved.getName())
                .keyPrefix(saved.getKeyPrefix())
                .maskedKey(saved.getMaskedKey())
                .secretKey(rawSecret) // Returned ONLY once
                .status(saved.getStatus())
                .scopes(saved.getScopes())
                .expiresAt(saved.getExpiresAt())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID orgId) {
        List<ApiKey> keys = apiKeyRepository.findAllByOrganizationIdOrderByCreatedAtDesc(orgId);
        return keys.stream().map(ApiKeyResponse::fromEntity).toList();
    }

    @Override
    @Transactional
    public ApiKeyCreatedResponse rotateApiKey(UUID orgId, UUID id) {
        ApiKey apiKey = apiKeyRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));

        String newSecret = generateRawSecret();
        String hashed = hashSecret(newSecret);
        String prefix = newSecret.substring(0, 14);
        String suffix = newSecret.substring(newSecret.length() - 4);
        String masked = prefix + "••••••••••••" + suffix;

        apiKey.setKeyPrefix(prefix);
        apiKey.setHashedSecret(hashed);
        apiKey.setMaskedKey(masked);
        apiKey.setStatus(ApiKeyStatus.ACTIVE);

        ApiKey updated = apiKeyRepository.save(apiKey);
        log.info("Rotated API key ID={} for org={}", updated.getId(), orgId);

        return ApiKeyCreatedResponse.builder()
                .id(updated.getId())
                .name(updated.getName())
                .keyPrefix(updated.getKeyPrefix())
                .maskedKey(updated.getMaskedKey())
                .secretKey(newSecret) // Returned ONLY once
                .status(updated.getStatus())
                .scopes(updated.getScopes())
                .expiresAt(updated.getExpiresAt())
                .createdAt(updated.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void revokeApiKey(UUID orgId, UUID id) {
        ApiKey apiKey = apiKeyRepository.findByIdAndOrganizationId(id, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiKey", id));

        apiKey.setStatus(ApiKeyStatus.REVOKED);
        apiKeyRepository.save(apiKey);
        log.info("Revoked API key ID={} for org={}", id, orgId);
    }

    private String generateRawSecret() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return "af_live_" + HexFormat.of().formatHex(randomBytes);
    }

    private String hashSecret(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm missing", e);
        }
    }
}
