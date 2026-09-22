package com.autoflow.modules.channel;

import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.channel.provider.whatsapp.WhatsAppChannelProvider;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WhatsAppChannelProvider Unit Tests")
class WhatsAppChannelProviderTest {

    private WhatsAppChannelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WhatsAppChannelProvider(
                "dummy_wa_app_id",
                "dummy_wa_app_secret",
                "http://localhost:8080/api/v1/channels/oauth/callback/WHATSAPP",
                "https://graph.facebook.com/v19.0",
                "dummy_phone_12345",
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Should return WHATSAPP channel type")
    void testChannelType() {
        assertEquals(ChannelType.WHATSAPP, provider.getChannelType());
    }

    @Test
    @DisplayName("Should generate valid OAuth dialog URL for WhatsApp Embedded Signup")
    void testGenerateAuthorizationUrl() {
        String authUrl = provider.generateAuthorizationUrl("test_nonce_999", "http://localhost:8080/cb");

        assertNotNull(authUrl);
        assertTrue(authUrl.contains("client_id=dummy_wa_app_id"));
        assertTrue(authUrl.contains("state=test_nonce_999"));
        assertTrue(authUrl.contains("redirect_uri=http://localhost:8080/cb"));
        assertTrue(authUrl.contains("whatsapp_business_messaging"));
    }

    @Test
    @DisplayName("Should exchange mock authorization code in mock mode")
    void testExchangeCodeMockMode() {
        SocialTokenResponse response = provider.exchangeCodeForTokens("mock_code", null);

        assertNotNull(response);
        assertNotNull(response.getAccessToken());
        assertTrue(response.getAccessToken().startsWith("mock_wa_access_token"));
        assertEquals("Bearer", response.getTokenType());
    }

    @Test
    @DisplayName("Should simulate sending message in mock mode")
    void testSendMessageMockMode() {
        String msgId = provider.sendMessage("mock_token", "+1555019999", "Hello WhatsApp lead!");

        assertNotNull(msgId);
        assertTrue(msgId.startsWith("mock_wa_msg"));
    }

    @Test
    @DisplayName("Should simulate sending media in mock mode")
    void testSendMediaMessageMockMode() {
        String mediaMsgId = provider.sendMediaMessage(
                "mock_token",
                "+1555019999",
                "IMAGE",
                "https://cdn.example.com/guide.png"
        );

        assertNotNull(mediaMsgId);
        assertTrue(mediaMsgId.startsWith("mock_wa_media"));
    }
}
