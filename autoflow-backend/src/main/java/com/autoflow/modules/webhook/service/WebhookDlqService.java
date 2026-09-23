package com.autoflow.modules.webhook.service;

import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface WebhookDlqService {

    WebhookDlqEntry recordDeadLetter(
            String provider,
            String eventId,
            String payload,
            String headers,
            String errorMessage
    );

    Page<WebhookDlqEntry> listDeadLetters(String provider, String status, int page, int size);

    WebhookDlqEntry replayDeadLetter(UUID id);

    void discardDeadLetter(UUID id);
}
