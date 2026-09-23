package com.autoflow.modules.settings.service;

import com.autoflow.modules.settings.dto.ApiKeyCreatedResponse;
import com.autoflow.modules.settings.dto.ApiKeyResponse;
import com.autoflow.modules.settings.dto.CreateApiKeyRequest;

import java.util.List;
import java.util.UUID;

public interface ApiKeyService {
    ApiKeyCreatedResponse createApiKey(UUID orgId, CreateApiKeyRequest request);
    List<ApiKeyResponse> listApiKeys(UUID orgId);
    ApiKeyCreatedResponse rotateApiKey(UUID orgId, UUID id);
    void revokeApiKey(UUID orgId, UUID id);
}
