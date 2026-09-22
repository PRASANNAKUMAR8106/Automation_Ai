package com.autoflow.modules.channel.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.channel.dto.ConnectedAccountResponse;
import com.autoflow.modules.channel.dto.OAuthCallbackRequest;
import com.autoflow.modules.channel.dto.OAuthInitiateResponse;
import com.autoflow.modules.channel.service.ChannelConnectionService;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels")
@RequiredArgsConstructor
@Tag(name = "Social Channels & Connections", description = "Endpoints for OAuth handshakes, connected social accounts, and webhook subscriptions")
public class ChannelConnectionController {

    private final ChannelConnectionService channelConnectionService;

    @GetMapping("/oauth/connect/{channel}")
    @Operation(summary = "Initiate OAuth Connection", description = "Generates secure OAuth authorization URL with encrypted state nonce")
    public ResponseEntity<ApiResponse<OAuthInitiateResponse>> initiateOAuth(
            @PathVariable ChannelType channel,
            @RequestParam(required = false) String redirectUri,
            Authentication authentication
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        UUID userId = parseUserId(authentication);
        OAuthInitiateResponse response = channelConnectionService.initiateOAuth(channel, orgId, userId, redirectUri);
        return ResponseEntity.ok(ApiResponse.ok("OAuth initiation URL generated", response));
    }

    @PostMapping("/oauth/callback/{channel}")
    @Operation(summary = "Handle OAuth Callback", description = "Exchanges authorization code for tokens, discovers profiles, and subscribes webhooks")
    public ResponseEntity<ApiResponse<List<ConnectedAccountResponse>>> handleOAuthCallback(
            @PathVariable ChannelType channel,
            @Valid @RequestBody OAuthCallbackRequest request
    ) {
        List<ConnectedAccountResponse> connected = channelConnectionService.handleOAuthCallback(channel, request);
        return ResponseEntity.ok(ApiResponse.ok("Channel accounts connected successfully", connected));
    }

    @GetMapping("/connected")
    @Operation(summary = "List Connected Accounts", description = "Retrieves all active social channel connections for current organization")
    public ResponseEntity<ApiResponse<List<ConnectedAccountResponse>>> getConnectedAccounts() {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<ConnectedAccountResponse> accounts = channelConnectionService.getConnectedAccounts(orgId);
        return ResponseEntity.ok(ApiResponse.ok("Connected accounts retrieved", accounts));
    }

    @PostMapping("/connected/{id}/refresh")
    @Operation(summary = "Refresh Account Token", description = "Manually triggers token validation and renewal with social provider")
    public ResponseEntity<ApiResponse<ConnectedAccountResponse>> refreshAccount(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        ConnectedAccountResponse response = channelConnectionService.refreshAccountToken(id, orgId);
        return ResponseEntity.ok(ApiResponse.ok("Account token refreshed successfully", response));
    }

    @DeleteMapping("/connected/{id}")
    @Operation(summary = "Disconnect Account", description = "Unsubscribes webhooks and revokes active connection")
    public ResponseEntity<ApiResponse<Void>> disconnectAccount(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        channelConnectionService.disconnectAccount(id, orgId);
        return ResponseEntity.ok(ApiResponse.ok("Account disconnected successfully", null));
    }

    private UUID parseUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return UUID.fromString(authentication.getPrincipal().toString());
            } catch (Exception ignored) {
            }
        }
        return UUID.randomUUID();
    }
}
