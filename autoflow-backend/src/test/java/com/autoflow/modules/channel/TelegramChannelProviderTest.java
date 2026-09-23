package com.autoflow.modules.channel;

import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.channel.provider.telegram.TelegramChannelProvider;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TelegramChannelProvider Unit Tests")
class TelegramChannelProviderTest {

    private TelegramChannelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TelegramChannelProvider(
                "dummy_tg_bot_token",
                "autoflow_demo_bot",
                "autoflow_telegram_secret_2026",
                "https://api.telegram.org",
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Should return TELEGRAM channel type")
    void testChannelType() {
        assertEquals(ChannelType.TELEGRAM, provider.getChannelType());
    }

    @Test
    @DisplayName("Should generate valid bot link with start parameter for state nonce")
    void testGenerateAuthorizationUrl() {
        String authUrl = provider.generateAuthorizationUrl("test_nonce_12345", "http://localhost:8080/cb");

        assertNotNull(authUrl);
        assertTrue(authUrl.contains("t.me/autoflow_demo_bot"));
        assertTrue(authUrl.contains("start=test_nonce_12345"));
    }

    @Test
    @DisplayName("Should exchange mock token in mock mode")
    void testExchangeCodeMockMode() {
        SocialTokenResponse response = provider.exchangeCodeForTokens("mock_tg_token", null);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertTrue(response.getAccessToken().startsWith("mock_tg_bot_token"));
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    @DisplayName("Should fetch connected bot profile in mock mode")
    void testFetchConnectedProfilesMockMode() {
        List<SocialAccountProfile> profiles = provider.fetchConnectedProfiles("mock_token");

        assertNotNull(profiles);
        assertEquals(1, profiles.size());
        SocialAccountProfile profile = profiles.get(0);
        assertEquals("@autoflow_demo_bot", profile.getAccountHandle());
        assertEquals("AutoFlow Demo Bot", profile.getAccountName());
    }

    @Test
    @DisplayName("Should simulate sending message in mock mode")
    void testSendMessageMockMode() {
        String msgId = provider.sendMessage("mock_token", "chat_123456", "Hello from AutoFlow Telegram Bot!");

        assertNotNull(msgId);
        assertTrue(msgId.startsWith("mock_tg_msg"));
    }

    @Test
    @DisplayName("Should simulate sending media in mock mode")
    void testSendMediaMessageMockMode() {
        String mediaMsgId = provider.sendMediaMessage(
                "mock_token",
                "chat_123456",
                "IMAGE",
                "https://cdn.example.com/promo.png"
        );

        assertNotNull(mediaMsgId);
        assertTrue(mediaMsgId.startsWith("mock_tg_media"));
    }
}
