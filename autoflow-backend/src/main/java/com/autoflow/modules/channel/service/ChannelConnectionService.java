package com.autoflow.modules.channel.service;

import com.autoflow.modules.channel.dto.ConnectedAccountResponse;
import com.autoflow.modules.channel.dto.OAuthCallbackRequest;
import com.autoflow.modules.channel.dto.OAuthInitiateResponse;
import com.autoflow.modules.crm.entity.ChannelType;

import java.util.List;
import java.util.UUID;

public interface ChannelConnectionService {

    OAuthInitiateResponse initiateOAuth(ChannelType channel, UUID orgId, UUID userId, String redirectUri);

    List<ConnectedAccountResponse> handleOAuthCallback(ChannelType channel, OAuthCallbackRequest request);

    List<ConnectedAccountResponse> getConnectedAccounts(UUID orgId);

    ConnectedAccountResponse refreshAccountToken(UUID accountId, UUID orgId);

    void disconnectAccount(UUID accountId, UUID orgId);
}
