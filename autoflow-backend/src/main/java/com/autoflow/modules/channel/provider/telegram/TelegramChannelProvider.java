package com.autoflow.modules.channel.provider.telegram;

import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.channel.spi.SocialChannelProvider;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Telegram Bot API implementation of SocialChannelProvider.
 * Supports Telegram Bot direct messages, webhook registration, and media dispatch.
 */
@Slf4j
@Component
public class TelegramChannelProvider implements SocialChannelProvider {

    private final String botToken;
    private final String botUsername;
    private final String webhookSecret;
    private final String apiBaseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public TelegramChannelProvider(
            @Value("${autoflow.channels.telegram.bot-token:dummy_telegram_bot_token}") String botToken,
            @Value("${autoflow.channels.telegram.bot-username:autoflow_demo_bot}") String botUsername,
            @Value("${autoflow.channels.telegram.webhook-secret:autoflow_telegram_secret_2026}") String webhookSecret,
            @Value("${autoflow.channels.telegram.api-base-url:https://api.telegram.org}") String apiBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.webhookSecret = webhookSecret;
        this.apiBaseUrl = apiBaseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(apiBaseUrl)
                .build();
    }

    public TelegramChannelProvider(
            String botToken,
            String botUsername,
            String webhookSecret,
            String apiBaseUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.webhookSecret = webhookSecret;
        this.apiBaseUrl = apiBaseUrl;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.TELEGRAM;
    }

    @Override
    public String generateAuthorizationUrl(String stateNonce, String redirectUri) {
        // Telegram bots connect via direct bot links with a start parameter containing the nonce
        return "https://t.me/" + botUsername + "?start=" + stateNonce;
    }

    @Override
    public SocialTokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        if (isMockMode()) {
            log.info("Mock mode active for Telegram Bot; returning synthetic token");
            return SocialTokenResponse.builder()
                    .accessToken("mock_tg_bot_token_" + System.currentTimeMillis())
                    .tokenType("Bearer")
                    .expiresInSeconds(315360000L) // Telegram bot tokens do not expire
                    .expiresAt(Instant.now().plusSeconds(315360000L))
                    .rawResponse("{\"mock\":true}")
                    .build();
        }

        String effectiveToken = (code != null && !code.isBlank()) ? code : botToken;
        try {
            String response = restClient.get()
                    .uri("/bot{token}/getMe", effectiveToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            boolean ok = root.path("ok").asBoolean(false);
            if (!ok) {
                throw new IllegalStateException("Telegram getMe returned ok=false: " + response);
            }

            return SocialTokenResponse.builder()
                    .accessToken(effectiveToken)
                    .tokenType("Bearer")
                    .expiresInSeconds(315360000L)
                    .expiresAt(Instant.now().plusSeconds(315360000L))
                    .rawResponse(response)
                    .build();
        } catch (Exception e) {
            log.error("Failed to validate Telegram bot token: {}", e.getMessage());
            throw new IllegalStateException("Telegram bot token verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SocialTokenResponse refreshLongLivedToken(String currentToken) {
        // Telegram bot tokens are perpetual until explicitly revoked via BotFather
        return SocialTokenResponse.builder()
                .accessToken(currentToken)
                .tokenType("Bearer")
                .expiresInSeconds(315360000L)
                .expiresAt(Instant.now().plusSeconds(315360000L))
                .build();
    }

    @Override
    public List<SocialAccountProfile> fetchConnectedProfiles(String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode active for Telegram profile fetch");
            SocialAccountProfile mockProfile = SocialAccountProfile.builder()
                    .externalAccountId("tg_bot_987654321")
                    .accountName("AutoFlow Demo Bot")
                    .accountHandle("@" + botUsername)
                    .pageId("tg_bot_987654321")
                    .pageAccessToken(accessToken)
                    .metadata(Map.of("username", botUsername, "channel", "TELEGRAM"))
                    .build();
            return List.of(mockProfile);
        }

        try {
            String response = restClient.get()
                    .uri("/bot{token}/getMe", accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("result");
            String botId = String.valueOf(result.path("id").asLong());
            String firstName = result.path("first_name").asText("Telegram Bot");
            String username = result.path("username").asText(botUsername);

            SocialAccountProfile profile = SocialAccountProfile.builder()
                    .externalAccountId(botId)
                    .accountName(firstName)
                    .accountHandle("@" + username)
                    .pageId(botId)
                    .pageAccessToken(accessToken)
                    .metadata(Map.of("username", username, "botId", botId))
                    .build();

            return List.of(profile);
        } catch (Exception e) {
            log.error("Failed to fetch Telegram bot profile: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void subscribeAppToWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode: Telegram webhook registered for bot {}", accountId);
            return;
        }

        try {
            String webhookUrl = "https://api.autoflow.ai/api/v1/webhooks/telegram/" + accountId;
            Map<String, Object> payload = Map.of(
                    "url", webhookUrl,
                    "secret_token", webhookSecret
            );

            restClient.post()
                    .uri("/bot{token}/setWebhook", accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully registered Telegram webhook for bot {}", accountId);
        } catch (Exception e) {
            log.warn("Failed to register Telegram webhook for bot {}: {}", accountId, e.getMessage());
        }
    }

    @Override
    public void unsubscribeAppFromWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode: Telegram webhook deleted for bot {}", accountId);
            return;
        }

        try {
            restClient.post()
                    .uri("/bot{token}/deleteWebhook", accessToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully removed Telegram webhook for bot {}", accountId);
        } catch (Exception e) {
            log.warn("Failed to delete Telegram webhook for bot {}: {}", accountId, e.getMessage());
        }
    }

    /**
     * Dispatches a text message to a Telegram chat.
     */
    public String sendMessage(String token, String chatId, String messageText) {
        if (isMockMode()) {
            log.info("Mock mode: simulating Telegram message to chat {} : {}", chatId, messageText);
            return "mock_tg_msg_" + System.currentTimeMillis();
        }

        try {
            String effectiveToken = (token != null && !token.isBlank() && !token.startsWith("mock_")) ? token : botToken;
            Map<String, Object> payload = Map.of(
                    "chat_id", chatId,
                    "text", messageText,
                    "parse_mode", "HTML"
            );

            String response = restClient.post()
                    .uri("/bot{token}/sendMessage", effectiveToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            long messageId = root.path("result").path("message_id").asLong(System.currentTimeMillis());
            return "tg_msg_" + messageId;
        } catch (Exception e) {
            log.error("Failed to send Telegram message to chat {}: {}", chatId, e.getMessage());
            throw new IllegalStateException("Failed to send Telegram message: " + e.getMessage(), e);
        }
    }

    /**
     * Dispatches a media message (photo or document) to a Telegram chat.
     */
    public String sendMediaMessage(String token, String chatId, String mediaType, String mediaUrl) {
        if (isMockMode()) {
            log.info("Mock mode: simulating Telegram media ({}) to chat {} : {}", mediaType, chatId, mediaUrl);
            return "mock_tg_media_" + System.currentTimeMillis();
        }

        try {
            String effectiveToken = (token != null && !token.isBlank() && !token.startsWith("mock_")) ? token : botToken;
            String endpoint = ("DOCUMENT".equalsIgnoreCase(mediaType)) ? "/bot{token}/sendDocument" : "/bot{token}/sendPhoto";
            String paramName = ("DOCUMENT".equalsIgnoreCase(mediaType)) ? "document" : "photo";

            Map<String, Object> payload = Map.of(
                    "chat_id", chatId,
                    paramName, mediaUrl
            );

            String response = restClient.post()
                    .uri(endpoint, effectiveToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            long messageId = root.path("result").path("message_id").asLong(System.currentTimeMillis());
            return "tg_media_" + messageId;
        } catch (Exception e) {
            log.error("Failed to send Telegram media message: {}", e.getMessage());
            throw new IllegalStateException("Failed to send Telegram media: " + e.getMessage(), e);
        }
    }

    private boolean isMockMode() {
        return botToken == null || botToken.startsWith("dummy") || botToken.startsWith("test");
    }
}
