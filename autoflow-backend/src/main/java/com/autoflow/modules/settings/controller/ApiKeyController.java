package com.autoflow.modules.settings.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.settings.dto.ApiKeyCreatedResponse;
import com.autoflow.modules.settings.dto.ApiKeyResponse;
import com.autoflow.modules.settings.dto.CreateApiKeyRequest;
import com.autoflow.modules.settings.service.ApiKeyService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settings/api-keys")
@RequiredArgsConstructor
@Tag(name = "Settings - API & Webhook Secrets", description = "Endpoints for managing masked API keys, secret generation, rotation, and revocation")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @GetMapping
    @Operation(summary = "List Organization API Keys", description = "Retrieves all API keys with secrets permanently masked")
    public ResponseEntity<ApiResponse<List<ApiKeyResponse>>> listApiKeys() {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<ApiKeyResponse> keys = apiKeyService.listApiKeys(orgId);
        return ResponseEntity.ok(ApiResponse.ok("API keys retrieved successfully", keys));
    }

    @PostMapping
    @Operation(summary = "Create API Key", description = "Generates a cryptographically secure API key. Secret token is only visible once in response.")
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponse>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("API key generated successfully", created));
    }

    @PostMapping("/{id}/rotate")
    @Operation(summary = "Rotate API Key Secret", description = "Revokes old secret and generates new cryptographic secret for this key")
    public ResponseEntity<ApiResponse<ApiKeyCreatedResponse>> rotateApiKey(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        ApiKeyCreatedResponse rotated = apiKeyService.rotateApiKey(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("API key secret rotated successfully", rotated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API Key", description = "Immediately revokes API key access")
    public ResponseEntity<ApiResponse<Void>> revokeApiKey(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        apiKeyService.revokeApiKey(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("API key revoked successfully", null));
    }
}
