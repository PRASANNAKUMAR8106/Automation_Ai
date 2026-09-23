package com.autoflow.modules.settings;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.settings.controller.ApiKeyController;
import com.autoflow.modules.settings.dto.ApiKeyCreatedResponse;
import com.autoflow.modules.settings.dto.ApiKeyResponse;
import com.autoflow.modules.settings.dto.CreateApiKeyRequest;
import com.autoflow.modules.settings.entity.ApiKeyStatus;
import com.autoflow.modules.settings.service.ApiKeyService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ApiKeyController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ApiKeyController Web MVC Tests")
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiKeyService apiKeyService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/settings/api-keys returns masked keys and never exposes secretKey")
    void testListApiKeys_MaskedAndSafe() throws Exception {
        UUID keyId = UUID.randomUUID();
        ApiKeyResponse keyResponse = ApiKeyResponse.builder()
                .id(keyId)
                .name("Production Webhook Key")
                .keyPrefix("af_live_a1b2c3")
                .maskedKey("af_live_a1b2c3••••••••••••4f2a")
                .status(ApiKeyStatus.ACTIVE)
                .scopes(List.of("webhook:read", "webhook:write"))
                .createdAt(Instant.now())
                .build();

        when(apiKeyService.listApiKeys(testOrgId)).thenReturn(List.of(keyResponse));

        mockMvc.perform(get("/api/v1/settings/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(keyId.toString()))
                .andExpect(jsonPath("$.data[0].name").value("Production Webhook Key"))
                .andExpect(jsonPath("$.data[0].maskedKey").value("af_live_a1b2c3••••••••••••4f2a"))
                .andExpect(jsonPath("$.data[0].secretKey").doesNotExist()); // Critical security verification
    }

    @Test
    @DisplayName("POST /api/v1/settings/api-keys creates key and returns plaintext secret once")
    void testCreateApiKey_ReturnsSecretOnce() throws Exception {
        UUID keyId = UUID.randomUUID();
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .name("Zapier Integration")
                .scopes(List.of("webhook:dispatch"))
                .expiresInDays(30)
                .build();

        ApiKeyCreatedResponse created = ApiKeyCreatedResponse.builder()
                .id(keyId)
                .name("Zapier Integration")
                .keyPrefix("af_live_9f8e7d")
                .maskedKey("af_live_9f8e7d••••••••••••1a2b")
                .secretKey("af_live_9f8e7d6c5b4a392817263548596041234567890123456789")
                .status(ApiKeyStatus.ACTIVE)
                .scopes(List.of("webhook:dispatch"))
                .createdAt(Instant.now())
                .build();

        when(apiKeyService.createApiKey(eq(testOrgId), any(CreateApiKeyRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/settings/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(keyId.toString()))
                .andExpect(jsonPath("$.data.secretKey").value("af_live_9f8e7d6c5b4a392817263548596041234567890123456789"))
                .andExpect(jsonPath("$.data.maskedKey").value("af_live_9f8e7d••••••••••••1a2b"));

        verify(apiKeyService).createApiKey(eq(testOrgId), any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/settings/api-keys/{id}/rotate rotates secret token")
    void testRotateApiKey() throws Exception {
        UUID keyId = UUID.randomUUID();
        ApiKeyCreatedResponse rotated = ApiKeyCreatedResponse.builder()
                .id(keyId)
                .name("Production Webhook Key")
                .keyPrefix("af_live_ff00aa")
                .maskedKey("af_live_ff00aa••••••••••••9988")
                .secretKey("af_live_ff00aabbccddeeff00112233445566778899aabbccddeeff")
                .status(ApiKeyStatus.ACTIVE)
                .scopes(List.of("all"))
                .createdAt(Instant.now())
                .build();

        when(apiKeyService.rotateApiKey(testOrgId, keyId)).thenReturn(rotated);

        mockMvc.perform(post("/api/v1/settings/api-keys/" + keyId + "/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.secretKey").value("af_live_ff00aabbccddeeff00112233445566778899aabbccddeeff"));

        verify(apiKeyService).rotateApiKey(testOrgId, keyId);
    }

    @Test
    @DisplayName("DELETE /api/v1/settings/api-keys/{id} revokes key")
    void testRevokeApiKey() throws Exception {
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/settings/api-keys/" + keyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(apiKeyService).revokeApiKey(testOrgId, keyId);
    }
}
