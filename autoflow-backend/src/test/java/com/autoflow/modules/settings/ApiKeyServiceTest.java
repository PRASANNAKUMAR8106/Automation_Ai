package com.autoflow.modules.settings;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.settings.dto.ApiKeyCreatedResponse;
import com.autoflow.modules.settings.dto.ApiKeyResponse;
import com.autoflow.modules.settings.dto.CreateApiKeyRequest;
import com.autoflow.modules.settings.entity.ApiKey;
import com.autoflow.modules.settings.entity.ApiKeyStatus;
import com.autoflow.modules.settings.repository.ApiKeyRepository;
import com.autoflow.modules.settings.service.ApiKeyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService Business Logic Unit Tests")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("createApiKey securely hashes secret, stores masked representation, and returns plaintext once")
    void testCreateApiKey() {
        CreateApiKeyRequest req = CreateApiKeyRequest.builder()
                .name("Stripe Webhook Listener")
                .scopes(List.of("webhook:events"))
                .expiresInDays(60)
                .build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> {
            ApiKey k = invocation.getArgument(0);
            k.setId(UUID.randomUUID());
            return k;
        });

        ApiKeyCreatedResponse resp = apiKeyService.createApiKey(testOrgId, req);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getName()).isEqualTo("Stripe Webhook Listener");
        assertThat(resp.getSecretKey()).startsWith("af_live_");
        assertThat(resp.getMaskedKey()).contains("••••••••••••");
        assertThat(resp.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(resp.getExpiresAt()).isNotNull();

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();

        assertThat(saved.getOrganizationId()).isEqualTo(testOrgId);
        // Verify raw secret was NEVER stored in hashedSecret!
        assertThat(saved.getHashedSecret()).isNotEqualTo(resp.getSecretKey());
        assertThat(saved.getHashedSecret()).hasSize(64); // SHA-256 hex string length
    }

    @Test
    @DisplayName("listApiKeys returns masked entities without plaintext secrets")
    void testListApiKeys() {
        ApiKey entity = ApiKey.builder()
                .name("Marketing Zap")
                .keyPrefix("af_live_123456")
                .maskedKey("af_live_123456••••••••••••9999")
                .hashedSecret("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
                .status(ApiKeyStatus.ACTIVE)
                .scopes(List.of("all"))
                .build();
        entity.setId(UUID.randomUUID());
        entity.setOrganizationId(testOrgId);

        when(apiKeyRepository.findAllByOrganizationIdOrderByCreatedAtDesc(testOrgId)).thenReturn(List.of(entity));

        List<ApiKeyResponse> responses = apiKeyService.listApiKeys(testOrgId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getName()).isEqualTo("Marketing Zap");
        assertThat(responses.get(0).getMaskedKey()).isEqualTo("af_live_123456••••••••••••9999");
    }

    @Test
    @DisplayName("rotateApiKey regenerates new secret and keeps tenant isolation")
    void testRotateApiKey() {
        UUID keyId = UUID.randomUUID();
        ApiKey existing = ApiKey.builder()
                .name("Old Key")
                .keyPrefix("af_live_000000")
                .maskedKey("af_live_000000••••••••••••0000")
                .hashedSecret("old_hash_value")
                .status(ApiKeyStatus.ACTIVE)
                .scopes(List.of("all"))
                .build();
        existing.setId(keyId);
        existing.setOrganizationId(testOrgId);

        when(apiKeyRepository.findByIdAndOrganizationId(keyId, testOrgId)).thenReturn(Optional.of(existing));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyCreatedResponse rotated = apiKeyService.rotateApiKey(testOrgId, keyId);

        assertThat(rotated.getSecretKey()).startsWith("af_live_");
        assertThat(existing.getHashedSecret()).isNotEqualTo("old_hash_value");
        assertThat(existing.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("revokeApiKey sets status to REVOKED")
    void testRevokeApiKey() {
        UUID keyId = UUID.randomUUID();
        ApiKey existing = ApiKey.builder()
                .name("Old Key")
                .keyPrefix("af_live_000000")
                .maskedKey("af_live_000000••••••••••••0000")
                .status(ApiKeyStatus.ACTIVE)
                .build();
        existing.setId(keyId);
        existing.setOrganizationId(testOrgId);

        when(apiKeyRepository.findByIdAndOrganizationId(keyId, testOrgId)).thenReturn(Optional.of(existing));

        apiKeyService.revokeApiKey(testOrgId, keyId);

        assertThat(existing.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        verify(apiKeyRepository).save(existing);
    }

    @Test
    @DisplayName("cross-tenant operations throw ResourceNotFoundException")
    void testCrossTenantIsolation() {
        UUID keyId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();

        when(apiKeyRepository.findByIdAndOrganizationId(keyId, testOrgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.rotateApiKey(testOrgId, keyId))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> apiKeyService.revokeApiKey(testOrgId, keyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
