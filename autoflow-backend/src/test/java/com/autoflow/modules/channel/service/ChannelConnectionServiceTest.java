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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelConnectionServiceTest {

    @Mock
    private ConnectedAccountRepository connectedAccountRepository;

    @Mock
    private SocialChannelProviderRegistry providerRegistry;

    @Mock
    private SocialChannelProvider socialChannelProvider;

    private TokenEncryptionService tokenEncryptionService;
    private ChannelConnectionService channelConnectionService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tokenEncryptionService = new TokenEncryptionService("test_aes256_secret_key_32bytes!");
        channelConnectionService = new ChannelConnectionServiceImpl(
                connectedAccountRepository,
                providerRegistry,
                tokenEncryptionService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("initiateOAuth generates valid encrypted state and delegates to provider")
    void testInitiateOAuth() {
        when(providerRegistry.getRequiredProvider(ChannelType.INSTAGRAM)).thenReturn(socialChannelProvider);
        when(socialChannelProvider.generateAuthorizationUrl(anyString(), any())).thenReturn("https://facebook.com/oauth/dialog?test=1");

        OAuthInitiateResponse response = channelConnectionService.initiateOAuth(
                ChannelType.INSTAGRAM,
                orgId,
                userId,
                "http://localhost:8080/callback"
        );

        assertNotNull(response);
        assertEquals(ChannelType.INSTAGRAM, response.getChannel());
        assertNotNull(response.getState());

        // Decrypt state and verify contents
        String decrypted = tokenEncryptionService.decrypt(response.getState());
        assertTrue(decrypted.contains(orgId.toString()));
        assertTrue(decrypted.contains(userId.toString()));
        assertTrue(decrypted.contains("INSTAGRAM"));
    }

    @Test
    @DisplayName("handleOAuthCallback rejects tampered state token with SecurityException")
    void testTamperedStateRejected() {
        OAuthCallbackRequest request = OAuthCallbackRequest.builder()
                .code("test_code")
                .state("invalid_corrupted_state_string")
                .build();

        assertThrows(SecurityException.class, () ->
                channelConnectionService.handleOAuthCallback(ChannelType.INSTAGRAM, request)
        );
    }

    @Test
    @DisplayName("handleOAuthCallback processes tokens, encrypts sensitive data, and saves ConnectedAccount")
    void testSuccessfulCallback() {
        long now = System.currentTimeMillis();
        String validState = tokenEncryptionService.encrypt(orgId + ":" + userId + ":INSTAGRAM:" + now + ":nonce");

        OAuthCallbackRequest request = OAuthCallbackRequest.builder()
                .code("valid_meta_code")
                .state(validState)
                .build();

        when(providerRegistry.getRequiredProvider(ChannelType.INSTAGRAM)).thenReturn(socialChannelProvider);
        when(socialChannelProvider.exchangeCodeForTokens(eq("valid_meta_code"), any())).thenReturn(
                SocialTokenResponse.builder()
                        .accessToken("plain_user_access_token_123")
                        .expiresInSeconds(5184000L)
                        .expiresAt(Instant.now().plusSeconds(5184000L))
                        .build()
        );

        SocialAccountProfile profile = SocialAccountProfile.builder()
                .externalAccountId("ig_acc_999")
                .accountName("Brand Store")
                .accountHandle("@brand_store")
                .pageId("page_123")
                .pageAccessToken("plain_page_token_456")
                .build();

        when(socialChannelProvider.fetchConnectedProfiles("plain_user_access_token_123"))
                .thenReturn(List.of(profile));

        when(connectedAccountRepository.findByOrganizationIdAndChannelAndExternalAccountId(orgId, ChannelType.INSTAGRAM, "ig_acc_999"))
                .thenReturn(Optional.empty());

        when(connectedAccountRepository.save(any(ConnectedAccount.class))).thenAnswer(invocation -> {
            ConnectedAccount ca = invocation.getArgument(0);
            ca.setId(UUID.randomUUID());
            return ca;
        });

        List<ConnectedAccountResponse> responses = channelConnectionService.handleOAuthCallback(ChannelType.INSTAGRAM, request);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("@brand_store", responses.getFirst().getAccountHandle());
        assertEquals(ChannelType.INSTAGRAM, responses.getFirst().getChannel());

        // Verify token saved is encrypted and not plaintext
        ArgumentCaptor<ConnectedAccount> accountCaptor = ArgumentCaptor.forClass(ConnectedAccount.class);
        verify(connectedAccountRepository).save(accountCaptor.capture());
        ConnectedAccount saved = accountCaptor.getValue();
        assertNotEquals("plain_page_token_456", saved.getEncryptedAccessToken());
        assertEquals("plain_page_token_456", tokenEncryptionService.decrypt(saved.getEncryptedAccessToken()));

        // Verify webhook subscription was triggered
        verify(socialChannelProvider).subscribeAppToWebhooks("page_123", "plain_page_token_456");
    }

    @Test
    @DisplayName("refreshAccountToken validates tenant isolation and refreshes credentials")
    void testRefreshAccountToken() {
        UUID accountId = UUID.randomUUID();
        String currentEncryptedToken = tokenEncryptionService.encrypt("current_token_abc");

        ConnectedAccount account = ConnectedAccount.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalAccountId("ig_acc_1")
                .encryptedAccessToken(currentEncryptedToken)
                .status(ConnectedAccountStatus.ACTIVE)
                .build();
        account.setId(accountId);
        account.setOrganizationId(orgId);

        when(connectedAccountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(providerRegistry.getRequiredProvider(ChannelType.INSTAGRAM)).thenReturn(socialChannelProvider);
        when(socialChannelProvider.refreshLongLivedToken("current_token_abc")).thenReturn(
                SocialTokenResponse.builder()
                        .accessToken("new_renewed_token_xyz")
                        .expiresInSeconds(5184000L)
                        .expiresAt(Instant.now().plusSeconds(5184000L))
                        .build()
        );
        when(connectedAccountRepository.save(any(ConnectedAccount.class))).thenAnswer(i -> i.getArgument(0));

        ConnectedAccountResponse response = channelConnectionService.refreshAccountToken(accountId, orgId);

        assertNotNull(response);
        verify(connectedAccountRepository).save(account);
        assertEquals("new_renewed_token_xyz", tokenEncryptionService.decrypt(account.getEncryptedAccessToken()));
    }

    @Test
    @DisplayName("Cross-tenant tampering on refresh throws SecurityException")
    void testCrossTenantTamperingThrowsSecurityException() {
        UUID accountId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();

        ConnectedAccount account = ConnectedAccount.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalAccountId("ig_acc_1")
                .build();
        account.setId(accountId);
        account.setOrganizationId(otherOrgId);

        when(connectedAccountRepository.findById(accountId)).thenReturn(Optional.of(account));

        assertThrows(SecurityException.class, () ->
                channelConnectionService.refreshAccountToken(accountId, orgId)
        );
    }
}
