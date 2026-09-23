package com.autoflow.modules.webhook.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import com.autoflow.modules.webhook.repository.WebhookDlqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook DLQ Service Tests")
class WebhookDlqServiceTest {

    @Mock
    private WebhookDlqRepository webhookDlqRepository;

    @Mock
    private WebhookReplayHandler mockInstagramHandler;

    private WebhookDlqServiceImpl dlqService;

    @BeforeEach
    void setUp() {
        lenient().when(mockInstagramHandler.supports("INSTAGRAM")).thenReturn(true);
        dlqService = new WebhookDlqServiceImpl(webhookDlqRepository, List.of(mockInstagramHandler));
    }

    @Test
    @DisplayName("Should record dead-letter entry accurately")
    void shouldRecordDeadLetter() {
        when(webhookDlqRepository.save(any(WebhookDlqEntry.class))).thenAnswer(i -> {
            WebhookDlqEntry e = i.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        WebhookDlqEntry result = dlqService.recordDeadLetter(
                "INSTAGRAM",
                "evt_failed_123",
                "{\"bad\":\"payload\"}",
                "X-Hub-Signature-256: sha256=123",
                "NullPointerException at line 42"
        );

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("INSTAGRAM", result.getProvider());
        assertEquals("evt_failed_123", result.getEventId());
        assertEquals("DEAD_LETTER", result.getStatus());
        assertEquals(1, result.getAttemptCount());

        verify(webhookDlqRepository, times(1)).save(any(WebhookDlqEntry.class));
    }

    @Test
    @DisplayName("Should successfully replay DLQ entry and update status to REPLAYED")
    void shouldReplayDeadLetterSuccessfully() {
        UUID entryId = UUID.randomUUID();
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(entryId)
                .provider("INSTAGRAM")
                .eventId("evt_replay_456")
                .payload("{\"entry\":[{\"id\":\"12345\"}]}")
                .status("DEAD_LETTER")
                .attemptCount(1)
                .receivedAt(Instant.now())
                .build();

        when(webhookDlqRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(webhookDlqRepository.save(any(WebhookDlqEntry.class))).thenAnswer(i -> i.getArgument(0));

        WebhookDlqEntry replayed = dlqService.replayDeadLetter(entryId);

        assertNotNull(replayed);
        assertEquals("REPLAYED", replayed.getStatus());
        assertEquals(2, replayed.getAttemptCount());
        assertNull(replayed.getErrorMessage());
        assertNotNull(replayed.getLastAttemptAt());

        verify(mockInstagramHandler, times(1)).replay(eq("{\"entry\":[{\"id\":\"12345\"}]}"));
        verify(webhookDlqRepository, times(1)).save(entry);
    }

    @Test
    @DisplayName("Should handle replay failure and record error message")
    void shouldHandleReplayFailure() {
        UUID entryId = UUID.randomUUID();
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(entryId)
                .provider("INSTAGRAM")
                .payload("{\"broken\":true}")
                .status("DEAD_LETTER")
                .attemptCount(1)
                .build();

        when(webhookDlqRepository.findById(entryId)).thenReturn(Optional.of(entry));
        doThrow(new RuntimeException("Simulated parser crash")).when(mockInstagramHandler).replay(anyString());

        Exception ex = assertThrows(RuntimeException.class, () -> dlqService.replayDeadLetter(entryId));
        assertTrue(ex.getMessage().contains("Simulated parser crash"));
        assertEquals("Simulated parser crash", entry.getErrorMessage());
        assertEquals(2, entry.getAttemptCount());
    }

    @Test
    @DisplayName("Should discard dead letter entry")
    void shouldDiscardDeadLetter() {
        UUID entryId = UUID.randomUUID();
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(entryId)
                .provider("INSTAGRAM")
                .status("DEAD_LETTER")
                .build();

        when(webhookDlqRepository.findById(entryId)).thenReturn(Optional.of(entry));

        dlqService.discardDeadLetter(entryId);

        assertEquals("DISCARDED", entry.getStatus());
        verify(webhookDlqRepository).save(entry);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when replaying non-existent ID")
    void shouldThrowNotFoundOnMissingEntry() {
        UUID missingId = UUID.randomUUID();
        when(webhookDlqRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> dlqService.replayDeadLetter(missingId));
    }
}
