package com.autoflow.modules.webhook.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import com.autoflow.modules.webhook.repository.WebhookDlqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDlqServiceImpl implements WebhookDlqService {

    private final WebhookDlqRepository webhookDlqRepository;
    private final List<WebhookReplayHandler> replayHandlers;

    @Override
    @Transactional
    public WebhookDlqEntry recordDeadLetter(
            String provider,
            String eventId,
            String payload,
            String headers,
            String errorMessage
    ) {
        log.warn("Recording webhook dead-letter entry for provider [{}], eventId [{}]: {}",
                provider, eventId, errorMessage);

        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .provider(provider != null ? provider.toUpperCase() : "UNKNOWN")
                .eventId(eventId)
                .payload(payload != null ? payload : "{}")
                .headers(headers)
                .errorMessage(errorMessage)
                .status("DEAD_LETTER")
                .attemptCount(1)
                .receivedAt(Instant.now())
                .build();

        return webhookDlqRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WebhookDlqEntry> listDeadLetters(String provider, String status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "receivedAt"));

        if (provider != null && !provider.isBlank() && status != null && !status.isBlank()) {
            return webhookDlqRepository.findByProviderAndStatus(provider.toUpperCase(), status.toUpperCase(), pageable);
        } else if (provider != null && !provider.isBlank()) {
            return webhookDlqRepository.findByProvider(provider.toUpperCase(), pageable);
        } else if (status != null && !status.isBlank()) {
            return webhookDlqRepository.findByStatus(status.toUpperCase(), pageable);
        }
        return webhookDlqRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public WebhookDlqEntry replayDeadLetter(UUID id) {
        WebhookDlqEntry entry = webhookDlqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookDlqEntry", id));

        entry.setAttemptCount(entry.getAttemptCount() + 1);
        entry.setLastAttemptAt(Instant.now());

        WebhookReplayHandler handler = replayHandlers.stream()
                .filter(h -> h.supports(entry.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No webhook replay handler registered for provider: " + entry.getProvider()));

        try {
            log.info("Replaying DLQ event [{}] for provider [{}]", id, entry.getProvider());
            handler.replay(entry.getPayload());
            entry.setStatus("REPLAYED");
            entry.setErrorMessage(null);
        } catch (Exception e) {
            log.error("Failed to replay DLQ event [{}]: {}", id, e.getMessage(), e);
            entry.setErrorMessage(e.getMessage());
            throw new RuntimeException("Replay failed: " + e.getMessage(), e);
        }

        return webhookDlqRepository.save(entry);
    }

    @Override
    @Transactional
    public void discardDeadLetter(UUID id) {
        WebhookDlqEntry entry = webhookDlqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookDlqEntry", id));
        entry.setStatus("DISCARDED");
        webhookDlqRepository.save(entry);
        log.info("Discarded DLQ event [{}]", id);
    }
}
