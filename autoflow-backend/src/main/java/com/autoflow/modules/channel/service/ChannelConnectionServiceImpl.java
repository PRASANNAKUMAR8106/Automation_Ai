package com.autoflow.modules.channel.service;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.modules.channel.dto.ConnectedAccountResponse;
import com.autoflow.modules.channel.dto.OAuthCallbackRequest;
import com.autoflow.modules.channel.dto.OAuthInitiateResponse;
import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.channel.spi.SocialChannelProvider;
import com.autoflow.modules.channel.spi.SocialChannelProviderRegistry;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelConnectionServiceImpl implements ChannelConnectionService {

    private static final long STATE_TTL_MILLIS = Duration.ofMinutes(15).toMillis();

    private final ConnectedAccountRepository connectedAccountRepository;
    private final SocialChannelProviderRegistry providerRegistry;
    private final TokenEncryptionService tokenEncryptionService;
    private final ObjectMapper objectMapper;

    @Override
    public OAuthInitiateResponse initiateOAuth(ChannelType channel, UUID orgId, UUID userId, String redirectUri) {
        SocialChannelProvider provider = providerRegistry.getRequiredProvider(channel);

        // Build cryptographically encrypted state containing orgId, userId, channel, timestamp, and nonce
        long now = System.currentTimeMillis();
        String rawState = orgId + ":" + userId + ":" + channel.name() + ":" + now + ":" + UUID.randomUUID();
        String encryptedState = tokenEncryptionService.encrypt(rawState);

        String authorizationUrl = provider.generateAuthorizationUrl(encryptedState, redirectUri);

        return OAuthInitiateResponse.builder()
                .authorizationUrl(authorizationUrl)
                .state(encryptedState)
                .channel(channel)
                .build();
    }

    @Override
    @Transactional
    public List<ConnectedAccountResponse> handleOAuthCallback(ChannelType channel, OAuthCallbackRequest request) {
        if (request.getError() != null && !request.getError().isBlank()) {
            throw new IllegalArgumentException("OAuth connection failed from provider: " + request.getErrorDescription());
        }

        // 1. Decrypt and validate state
        String decryptedState;
        try {
            decryptedState = tokenEncryptionService.decrypt(request.getState());
        } catch (Exception e) {
            throw new SecurityException("Invalid or tampered OAuth state parameter", e);
        }

        String[] parts = decryptedState.split(":");
        if (parts.length < 5) {
            throw new SecurityException("Malformed OAuth state format");
        }

        UUID orgId = UUID.fromString(parts[0]);
        ChannelType stateChannel = ChannelType.valueOf(parts[2]);
        long timestamp = Long.parseLong(parts[3]);

        if (System.currentTimeMillis() - timestamp > STATE_TTL_MILLIS) {
            throw new IllegalStateException("OAuth handshake session has expired. Please try connecting again.");
        }

        if (stateChannel != channel) {
            throw new SecurityException("Channel mismatch between state and request path");
        }

        // 2. Exchange code with Provider SPI
        SocialChannelProvider provider = providerRegistry.getRequiredProvider(channel);
        SocialTokenResponse tokenResponse = provider.exchangeCodeForTokens(request.getCode(), null);

        // 3. Discover connected accounts / profiles
        List<SocialAccountProfile> profiles = provider.fetchConnectedProfiles(tokenResponse.getAccessToken());
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No eligible " + channel + " accounts were found in this profile.");
        }

        List<ConnectedAccountResponse> results = new ArrayList<>();

        for (SocialAccountProfile profile : profiles) {
            // Choose the token to encrypt (page-scoped access token or user long-lived token)
            String rawToken = (profile.getPageAccessToken() != null && !profile.getPageAccessToken().isBlank())
                    ? profile.getPageAccessToken()
                    : tokenResponse.getAccessToken();

            String encryptedToken = tokenEncryptionService.encrypt(rawToken);

            // Upsert ConnectedAccount
            Optional<ConnectedAccount> existingOpt = connectedAccountRepository
                    .findByOrganizationIdAndChannelAndExternalAccountId(orgId, channel, profile.getExternalAccountId());

            ConnectedAccount account;
            if (existingOpt.isPresent()) {
                account = existingOpt.get();
                account.setAccountName(profile.getAccountName());
                account.setAccountHandle(profile.getAccountHandle());
                account.setEncryptedAccessToken(encryptedToken);
                account.setTokenExpiresAt(tokenResponse.getExpiresAt());
                account.setStatus(ConnectedAccountStatus.ACTIVE);
            } else {
                account = ConnectedAccount.builder()
                        .channel(channel)
                        .externalAccountId(profile.getExternalAccountId())
                        .accountName(profile.getAccountName())
                        .accountHandle(profile.getAccountHandle())
                        .encryptedAccessToken(encryptedToken)
                        .tokenExpiresAt(tokenResponse.getExpiresAt())
                        .status(ConnectedAccountStatus.ACTIVE)
                        .build();
                account.setOrganizationId(orgId);
            }

            try {
                account.setMetadata(objectMapper.writeValueAsString(profile.getMetadata()));
            } catch (Exception e) {
                log.warn("Could not serialize profile metadata: {}", e.getMessage());
            }

            ConnectedAccount saved = connectedAccountRepository.save(account);

            // 4. Subscribe account to AutoFlow Webhooks
            String webhookTargetId = profile.getPageId() != null ? profile.getPageId() : profile.getExternalAccountId();
            provider.subscribeAppToWebhooks(webhookTargetId, rawToken);

            results.add(ConnectedAccountResponse.fromEntity(saved));
            log.info("Successfully connected account [{}] ({}) for org [{}]", saved.getAccountHandle(), channel, orgId);
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectedAccountResponse> getConnectedAccounts(UUID orgId) {
        return connectedAccountRepository.findByOrganizationId(orgId).stream()
                .map(ConnectedAccountResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public ConnectedAccountResponse refreshAccountToken(UUID accountId, UUID orgId) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Connected account not found: " + accountId));

        if (!account.getOrganizationId().equals(orgId)) {
            throw new SecurityException("Tenant access violation: account does not belong to organization");
        }

        String decryptedToken = tokenEncryptionService.decrypt(account.getEncryptedAccessToken());
        SocialChannelProvider provider = providerRegistry.getRequiredProvider(account.getChannel());

        SocialTokenResponse refreshed = provider.refreshLongLivedToken(decryptedToken);
        account.setEncryptedAccessToken(tokenEncryptionService.encrypt(refreshed.getAccessToken()));
        account.setTokenExpiresAt(refreshed.getExpiresAt());
        account.setStatus(ConnectedAccountStatus.ACTIVE);

        ConnectedAccount saved = connectedAccountRepository.save(account);
        log.info("Refreshed token for connected account {} ({})", saved.getId(), saved.getChannel());
        return ConnectedAccountResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public void disconnectAccount(UUID accountId, UUID orgId) {
        ConnectedAccount account = connectedAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Connected account not found: " + accountId));

        if (!account.getOrganizationId().equals(orgId)) {
            throw new SecurityException("Tenant access violation: account does not belong to organization");
        }

        try {
            String decryptedToken = tokenEncryptionService.decrypt(account.getEncryptedAccessToken());
            SocialChannelProvider provider = providerRegistry.getRequiredProvider(account.getChannel());
            provider.unsubscribeAppFromWebhooks(account.getExternalAccountId(), decryptedToken);
        } catch (Exception e) {
            log.warn("Error unsubscribing webhooks during disconnect of account {}: {}", accountId, e.getMessage());
        }

        account.setStatus(ConnectedAccountStatus.DISCONNECTED);
        connectedAccountRepository.save(account);
        log.info("Disconnected channel account {} for org {}", accountId, orgId);
    }
}
