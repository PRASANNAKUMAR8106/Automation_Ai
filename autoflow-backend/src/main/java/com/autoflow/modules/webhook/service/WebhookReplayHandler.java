package com.autoflow.modules.webhook.service;

public interface WebhookReplayHandler {

    boolean supports(String provider);

    void replay(String payload);
}
