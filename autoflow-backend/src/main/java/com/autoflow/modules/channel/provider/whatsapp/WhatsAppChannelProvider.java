package com.autoflow.modules.channel.provider.whatsapp;

import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.channel.spi.SocialChannelProvider;
import com.autoflow.modules.crm.entity.ChannelType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Meta Cloud API implementation for WhatsApp Business Accounts (WABA).
 * Supports Embedded Signup, sending text/media messages, and webhook subscriptions.
 */
@Slf4j
@Component
public class WhatsAppChannelProvider implements SocialChannelProvider {

    private static final String DEFAULT_SCOPES =
            "whatsapp_business_management,whatsapp_business_messaging";

    private final String appId;
    private final String appSecret;
    private final String defaultRedirectUri;
    private final String graphApiBaseUrl;
    private final String defaultPhoneNumberId;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public WhatsAppChannelProvider(
            @Value("${autoflow.channels.whatsapp.app-id:dummy_wa_app_id}") String appId,
            @Value("${autoflow.channels.whatsapp.app-secret:dummy_wa_app_secret}") String appSecret,
            @Value("${autoflow.channels.whatsapp.redirect-uri:http://localhost:8080/api/v1/channels/oauth/callback/WHATSAPP}") String defaultRedirectUri,
            @Value("${autoflow.channels.whatsapp.graph-api-base-url:https://graph.facebook.com/v19.0}") String graphApiBaseUrl,
            @Value("${autoflow.channels.whatsapp.phone-number-id:dummy_wa_phone_id}") String defaultPhoneNumberId,
            ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.defaultRedirectUri = defaultRedirectUri;
        this.graphApiBaseUrl = graphApiBaseUrl;
        this.defaultPhoneNumberId = defaultPhoneNumberId;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(graphApiBaseUrl)
                .build();
    }

    public WhatsAppChannelProvider(
            String appId,
            String appSecret,
            String defaultRedirectUri,
            String graphApiBaseUrl,
            String defaultPhoneNumberId,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.defaultRedirectUri = defaultRedirectUri;
        this.graphApiBaseUrl = graphApiBaseUrl;
        this.defaultPhoneNumberId = defaultPhoneNumberId;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WHATSAPP;
    }

    @Override
    public String generateAuthorizationUrl(String stateNonce, String redirectUri) {
        String effectiveRedirect = (redirectUri != null && !redirectUri.isBlank()) ? redirectUri : defaultRedirectUri;
        return UriComponentsBuilder.fromHttpUrl("https://www.facebook.com/v19.0/dialog/oauth")
                .queryParam("client_id", appId)
                .queryParam("redirect_uri", effectiveRedirect)
                .queryParam("scope", DEFAULT_SCOPES)
                .queryParam("state", stateNonce)
                .queryParam("response_type", "code")
                .build()
                .toUriString();
    }

    @Override
    public SocialTokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        String effectiveRedirect = (redirectUri != null && !redirectUri.isBlank()) ? redirectUri : defaultRedirectUri;

        if (isMockMode()) {
            log.info("Mock mode active for WhatsApp Cloud API; returning synthetic token");
            return SocialTokenResponse.builder()
                    .accessToken("mock_wa_access_token_" + System.currentTimeMillis())
                    .tokenType("Bearer")
                    .expiresInSeconds(5184000L)
                    .expiresAt(Instant.now().plusSeconds(5184000L))
                    .rawResponse("{\"mock\":true}")
                    .build();
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/oauth/access_token")
                            .queryParam("client_id", appId)
                            .queryParam("client_secret", appSecret)
                            .queryParam("redirect_uri", effectiveRedirect)
                            .queryParam("code", code)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String accessToken = root.path("access_token").asText();
            long expiresIn = root.path("expires_in").asLong(5184000L);

            return SocialTokenResponse.builder()
                    .accessToken(accessToken)
                    .tokenType("Bearer")
                    .expiresInSeconds(expiresIn)
                    .expiresAt(Instant.now().plusSeconds(expiresIn))
                    .rawResponse(response)
                    .build();
        } catch (Exception e) {
            log.error("Failed to exchange WhatsApp OAuth code: {}", e.getMessage());
            throw new IllegalStateException("WhatsApp code exchange failed: " + e.getMessage(), e);
        }
    }

    @Override
    public SocialTokenResponse refreshLongLivedToken(String currentToken) {
        if (isMockMode()) {
            return SocialTokenResponse.builder()
                    .accessToken("mock_wa_refreshed_token_" + System.currentTimeMillis())
                    .tokenType("Bearer")
                    .expiresInSeconds(5184000L)
                    .expiresAt(Instant.now().plusSeconds(5184000L))
                    .build();
        }
        return exchangeCodeForTokens(currentToken, defaultRedirectUri);
    }

    @Override
    public List<SocialAccountProfile> fetchConnectedProfiles(String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode active for WhatsApp profile fetch");
            SocialAccountProfile mockProfile = SocialAccountProfile.builder()
                    .externalAccountId(defaultPhoneNumberId)
                    .accountName("AutoFlow Demo WhatsApp")
                    .accountHandle("+15550199999")
                    .pageId("waba_demo_12345")
                    .pageAccessToken(accessToken)
                    .metadata(Map.of("phoneNumberId", defaultPhoneNumberId, "wabaId", "waba_demo_12345"))
                    .build();
            return List.of(mockProfile);
        }

        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me")
                            .queryParam("fields", "id,name")
                            .queryParam("access_token", accessToken)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String wabaId = root.path("id").asText();
            String name = root.path("name").asText("WhatsApp Business");

            SocialAccountProfile profile = SocialAccountProfile.builder()
                    .externalAccountId(defaultPhoneNumberId)
                    .accountName(name)
                    .accountHandle("+WhatsApp")
                    .pageId(wabaId)
                    .pageAccessToken(accessToken)
                    .metadata(Map.of("phoneNumberId", defaultPhoneNumberId, "wabaId", wabaId))
                    .build();

            return List.of(profile);
        } catch (Exception e) {
            log.error("Failed to fetch WhatsApp connected profiles: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public void subscribeAppToWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode: WhatsApp webhook subscribed for WABA {}", accountId);
            return;
        }
        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + accountId + "/subscribed_apps")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully subscribed WhatsApp webhooks for WABA {}", accountId);
        } catch (Exception e) {
            log.warn("Failed to subscribe WhatsApp webhooks for WABA {}: {}", accountId, e.getMessage());
        }
    }

    @Override
    public void unsubscribeAppFromWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode: WhatsApp webhook unsubscribed for WABA {}", accountId);
            return;
        }
        try {
            restClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + accountId + "/subscribed_apps")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to unsubscribe WhatsApp webhooks: {}", e.getMessage());
        }
    }

    /**
     * Dispatches a WhatsApp text message via Cloud API.
     */
    public String sendMessage(String accessToken, String recipientPhone, String messageText) {
        if (isMockMode()) {
            log.info("Mock mode: simulating WhatsApp message to {} : {}", recipientPhone, messageText);
            return "mock_wa_msg_" + System.currentTimeMillis();
        }

        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", recipientPhone,
                    "type", "text",
                    "text", Map.of("body", messageText)
            );

            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + defaultPhoneNumberId + "/messages")
                            .queryParam("access_token", accessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            return node.path("messages").path(0).path("id").asText("wa_msg_" + System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp message to {}: {}", recipientPhone, e.getMessage());
            throw new IllegalStateException("Failed to send WhatsApp message: " + e.getMessage(), e);
        }
    }

    /**
     * Dispatches a WhatsApp media message (document or image).
     */
    public String sendMediaMessage(String accessToken, String recipientPhone, String mediaType, String mediaUrl) {
        if (isMockMode()) {
            log.info("Mock mode: simulating WhatsApp media ({}) to {} : {}", mediaType, recipientPhone, mediaUrl);
            return "mock_wa_media_" + System.currentTimeMillis();
        }

        try {
            String type = ("IMAGE".equalsIgnoreCase(mediaType)) ? "image" : "document";
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", recipientPhone,
                    "type", type,
                    type, Map.of("link", mediaUrl)
            );

            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + defaultPhoneNumberId + "/messages")
                            .queryParam("access_token", accessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            return node.path("messages").path(0).path("id").asText("wa_media_" + System.currentTimeMillis());
        } catch (Exception e) {
            log.error("Failed to send WhatsApp media message: {}", e.getMessage());
            throw new IllegalStateException("Failed to send WhatsApp media: " + e.getMessage(), e);
        }
    }

    private boolean isMockMode() {
        return appId == null || appId.startsWith("dummy") || appId.startsWith("test");
    }
}
