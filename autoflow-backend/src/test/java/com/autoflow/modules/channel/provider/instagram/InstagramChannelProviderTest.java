package com.autoflow.modules.channel.provider.instagram;

import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstagramChannelProviderTest {

    private InstagramChannelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new InstagramChannelProvider(
                "test_meta_app_id",
                "test_meta_app_secret",
                "http://localhost:8080/api/v1/channels/oauth/callback/INSTAGRAM",
                "https://graph.facebook.com/v19.0",
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Provider correctly identifies as INSTAGRAM channel type")
    void testChannelType() {
        assertEquals(ChannelType.INSTAGRAM, provider.getChannelType());
    }

    @Test
    @DisplayName("generateAuthorizationUrl builds valid Facebook OAuth 2.0 URL with required scopes")
    void testGenerateAuthorizationUrl() {
        String state = "secure_encrypted_state_12345";
        String url = provider.generateAuthorizationUrl(state, null);

        assertNotNull(url);
        assertTrue(url.startsWith("https://www.facebook.com/v19.0/dialog/oauth"));
        assertTrue(url.contains("client_id=test_meta_app_id"));
        assertTrue(url.contains("state=" + state));
        assertTrue(url.contains("scope=instagram_basic"));
        assertTrue(url.contains("instagram_manage_comments"));
        assertTrue(url.contains("instagram_manage_messages"));
        assertTrue(url.contains("pages_manage_metadata"));
    }

    @Test
    @DisplayName("exchangeCodeForTokens returns 60-day long-lived token in test mode")
    void testExchangeCodeForTokens() {
        SocialTokenResponse response = provider.exchangeCodeForTokens("sample_auth_code_xyz", null);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertEquals("bearer", response.getTokenType());
        assertEquals(5184000L, response.getExpiresInSeconds());
        assertNotNull(response.getExpiresAt());
    }

    @Test
    @DisplayName("refreshLongLivedToken renews access token with new expiration")
    void testRefreshLongLivedToken() {
        SocialTokenResponse response = provider.refreshLongLivedToken("existing_valid_token");

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertTrue(response.getAccessToken().startsWith("mock_refreshed_token_"));
    }

    @Test
    @DisplayName("fetchConnectedProfiles discovers linked Instagram Business accounts")
    void testFetchConnectedProfiles() {
        List<SocialAccountProfile> profiles = provider.fetchConnectedProfiles("valid_access_token");

        assertNotNull(profiles);
        assertFalse(profiles.isEmpty());

        SocialAccountProfile profile = profiles.getFirst();
        assertEquals("17841405822304914", profile.getExternalAccountId());
        assertEquals("AutoFlow Official", profile.getAccountName());
        assertEquals("@autoflow_ai", profile.getAccountHandle());
        assertNotNull(profile.getPageId());
        assertNotNull(profile.getPageAccessToken());
    }

    @Test
    @DisplayName("Webhook subscription and unsubscription execute without errors")
    void testWebhookSubscriptions() {
        assertDoesNotThrow(() -> provider.subscribeAppToWebhooks("17841405822304914", "page_token_xyz"));
        assertDoesNotThrow(() -> provider.unsubscribeAppFromWebhooks("17841405822304914", "page_token_xyz"));
    }
}
