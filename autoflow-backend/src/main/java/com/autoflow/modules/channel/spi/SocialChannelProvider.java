package com.autoflow.modules.channel.spi;

import com.autoflow.modules.channel.dto.SocialAccountProfile;
import com.autoflow.modules.channel.dto.SocialTokenResponse;
import com.autoflow.modules.crm.entity.ChannelType;

import java.util.List;

/**
 * Service Provider Interface (SPI) for social-media channel integrations.
 * Abstracted across Instagram Graph API, WhatsApp Cloud API, Facebook Messenger, etc.
 */
public interface SocialChannelProvider {

    /**
     * The channel type handled by this provider.
     */
    ChannelType getChannelType();

    /**
     * Builds the OAuth 2.0 authorization URL for connecting this social channel.
     *
     * @param stateNonce  cryptographically secure CSRF state token
     * @param redirectUri registered callback URI
     * @return full authorization redirect URL
     */
    String generateAuthorizationUrl(String stateNonce, String redirectUri);

    /**
     * Exchanges OAuth authorization code for long-lived access tokens.
     *
     * @param code        authorization code received from provider callback
     * @param redirectUri matching redirect URI used in handshake
     * @return access token and expiration details
     */
    SocialTokenResponse exchangeCodeForTokens(String code, String redirectUri);

    /**
     * Refreshes a long-lived access token before expiration.
     *
     * @param currentToken active or expiring access token
     * @return refreshed access token and updated expiration
     */
    SocialTokenResponse refreshLongLivedToken(String currentToken);

    /**
     * Discovers and retrieves available social accounts / profiles accessible by this token.
     * For Instagram/Facebook, discovers connected Pages and linked Instagram Business Accounts.
     *
     * @param accessToken valid user or system access token
     * @return list of discoverable accounts
     */
    List<SocialAccountProfile> fetchConnectedProfiles(String accessToken);

    /**
     * Subscribes AutoFlow webhook listener to this account's event streams.
     *
     * @param accountId   external social account or page ID
     * @param accessToken page or channel access token
     */
    void subscribeAppToWebhooks(String accountId, String accessToken);

    /**
     * Unsubscribes AutoFlow webhook listener upon disconnection or revocation.
     *
     * @param accountId   external social account or page ID
     * @param accessToken page or channel access token
     */
    void unsubscribeAppFromWebhooks(String accountId, String accessToken);
}
