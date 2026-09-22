package com.autoflow.modules.channel.provider.instagram;

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
 * Meta Graph API implementation for Instagram Professional and Business accounts.
 * Handles OAuth code exchange, 60-day token lengthening, profile discovery, and webhook subscriptions.
 */
@Slf4j
@Component
public class InstagramChannelProvider implements SocialChannelProvider {

    private static final String DEFAULT_SCOPES =
            "instagram_basic,instagram_manage_comments,instagram_manage_messages,pages_show_list,pages_read_engagement,pages_manage_metadata";

    private final String appId;
    private final String appSecret;
    private final String defaultRedirectUri;
    private final String graphApiBaseUrl;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public InstagramChannelProvider(
            @Value("${autoflow.channels.meta.app-id:dummy_meta_app_id}") String appId,
            @Value("${autoflow.channels.meta.app-secret:dummy_meta_app_secret}") String appSecret,
            @Value("${autoflow.channels.meta.redirect-uri:http://localhost:8080/api/v1/channels/oauth/callback/INSTAGRAM}") String defaultRedirectUri,
            @Value("${autoflow.channels.meta.graph-api-base-url:https://graph.facebook.com/v19.0}") String graphApiBaseUrl,
            ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.defaultRedirectUri = defaultRedirectUri;
        this.graphApiBaseUrl = graphApiBaseUrl;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(graphApiBaseUrl)
                .build();
    }

    // Constructor overload allowing mock RestClient injection in unit tests
    public InstagramChannelProvider(
            String appId,
            String appSecret,
            String defaultRedirectUri,
            String graphApiBaseUrl,
            RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.defaultRedirectUri = defaultRedirectUri;
        this.graphApiBaseUrl = graphApiBaseUrl;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChannelType getChannelType() {
        return ChannelType.INSTAGRAM;
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

        // In test mode or when using dummy credentials, return mock long-lived token
        if (isMockMode()) {
            log.info("Mock mode active for Meta Graph API; returning synthetic long-lived token");
            return SocialTokenResponse.builder()
                    .accessToken("mock_meta_long_lived_token_" + System.currentTimeMillis())
                    .tokenType("bearer")
                    .expiresInSeconds(5184000L) // 60 days
                    .expiresAt(Instant.now().plusSeconds(5184000L))
                    .rawResponse("{\"access_token\":\"mock\",\"token_type\":\"bearer\",\"expires_in\":5184000}")
                    .build();
        }

        try {
            // Step 1: Exchange code for short-lived user access token
            String shortLivedResponse = restClient.get()
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

            JsonNode shortLivedNode = objectMapper.readTree(shortLivedResponse);
            String shortLivedToken = shortLivedNode.path("access_token").asText();

            // Step 2: Exchange short-lived token for 60-day long-lived token
            return exchangeForLongLivedToken(shortLivedToken);
        } catch (Exception e) {
            log.error("Failed to exchange OAuth code with Meta Graph API: {}", e.getMessage());
            throw new IllegalStateException("Failed to exchange OAuth code with Meta Graph API: " + e.getMessage(), e);
        }
    }

    @Override
    public SocialTokenResponse refreshLongLivedToken(String currentToken) {
        if (isMockMode()) {
            return SocialTokenResponse.builder()
                    .accessToken("mock_refreshed_token_" + System.currentTimeMillis())
                    .tokenType("bearer")
                    .expiresInSeconds(5184000L)
                    .expiresAt(Instant.now().plusSeconds(5184000L))
                    .rawResponse("{\"access_token\":\"refreshed\",\"expires_in\":5184000}")
                    .build();
        }

        try {
            return exchangeForLongLivedToken(currentToken);
        } catch (Exception e) {
            log.error("Failed to refresh Meta long-lived token: {}", e.getMessage());
            throw new IllegalStateException("Failed to refresh Meta long-lived token: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SocialAccountProfile> fetchConnectedProfiles(String accessToken) {
        if (isMockMode()) {
            List<SocialAccountProfile> mockProfiles = new ArrayList<>();
            mockProfiles.add(SocialAccountProfile.builder()
                    .externalAccountId("17841405822304914")
                    .accountName("AutoFlow Official")
                    .accountHandle("@autoflow_ai")
                    .pageId("104829104810928")
                    .pageAccessToken("mock_page_access_token_123")
                    .profilePictureUrl("https://autoflow.ai/assets/avatar.png")
                    .metadata(Map.of("category", "Software Company", "followers_count", 12500))
                    .build());
            return mockProfiles;
        }

        try {
            String accountsResponse = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me/accounts")
                            .queryParam("fields", "id,name,access_token,instagram_business_account{id,username,name,profile_picture_url}")
                            .queryParam("access_token", accessToken)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            List<SocialAccountProfile> profiles = new ArrayList<>();
            JsonNode root = objectMapper.readTree(accountsResponse);
            JsonNode data = root.path("data");

            if (data.isArray()) {
                for (JsonNode pageNode : data) {
                    String pageId = pageNode.path("id").asText();
                    String pageName = pageNode.path("name").asText();
                    String pageAccessToken = pageNode.path("access_token").asText();

                    JsonNode igNode = pageNode.path("instagram_business_account");
                    if (!igNode.isMissingNode() && !igNode.isNull()) {
                        String igId = igNode.path("id").asText();
                        String username = igNode.path("username").asText();
                        String igName = igNode.hasNonNull("name") ? igNode.path("name").asText() : pageName;
                        String profilePic = igNode.path("profile_picture_url").asText(null);

                        profiles.add(SocialAccountProfile.builder()
                                .externalAccountId(igId)
                                .accountName(igName)
                                .accountHandle("@" + username)
                                .pageId(pageId)
                                .pageAccessToken(pageAccessToken)
                                .profilePictureUrl(profilePic)
                                .metadata(Map.of("pageId", pageId, "pageName", pageName))
                                .build());
                    }
                }
            }

            return profiles;
        } catch (Exception e) {
            log.error("Failed to fetch Instagram profiles from Meta Graph API: {}", e.getMessage());
            throw new IllegalStateException("Failed to fetch Instagram profiles from Meta Graph API: " + e.getMessage(), e);
        }
    }

    @Override
    public void subscribeAppToWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode active; simulating webhook subscription for account {}", accountId);
            return;
        }

        try {
            restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/" + accountId + "/subscribed_apps")
                            .queryParam("subscribed_fields", "messages,message_reactions,comments,mentions")
                            .queryParam("access_token", accessToken)
                            .build())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully subscribed Meta webhooks for account {}", accountId);
        } catch (Exception e) {
            log.warn("Could not subscribe Meta webhooks for account {}: {}", accountId, e.getMessage());
        }
    }

    @Override
    public void unsubscribeAppFromWebhooks(String accountId, String accessToken) {
        if (isMockMode()) {
            log.info("Mock mode active; simulating webhook unsubscription for account {}", accountId);
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

            log.info("Successfully unsubscribed Meta webhooks for account {}", accountId);
        } catch (Exception e) {
            log.warn("Could not unsubscribe Meta webhooks for account {}: {}", accountId, e.getMessage());
        }
    }

    private SocialTokenResponse exchangeForLongLivedToken(String shortLivedToken) throws Exception {
        String longLivedResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("fb_exchange_token", shortLivedToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);

        JsonNode longLivedNode = objectMapper.readTree(longLivedResponse);
        String longLivedToken = longLivedNode.path("access_token").asText();
        long expiresIn = longLivedNode.path("expires_in").asLong(5184000L); // Default 60 days

        return SocialTokenResponse.builder()
                .accessToken(longLivedToken)
                .tokenType(longLivedNode.path("token_type").asText("bearer"))
                .expiresInSeconds(expiresIn)
                .expiresAt(Instant.now().plusSeconds(expiresIn))
                .rawResponse(longLivedResponse)
                .build();
    }

    private boolean isMockMode() {
        return appId == null || appId.startsWith("dummy") || appId.startsWith("test");
    }
}
