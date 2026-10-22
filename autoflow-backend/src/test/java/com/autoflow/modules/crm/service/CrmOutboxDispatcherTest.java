package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.CrmOutboxMessage;
import com.autoflow.modules.crm.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrmOutboxDispatcher Unit Tests")
class CrmOutboxDispatcherTest {

    @Mock
    private CrmOutboxService outboxService;
    @Mock
    private CrmService crmService;

    private CrmOutboxDispatcher dispatcher;

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        dispatcher = new CrmOutboxDispatcher(outboxService, crmService);
    }

    @Test
    @DisplayName("Should re-dispatch an abandoned entry and mark it dispatched")
    void shouldRetryAbandonedEntry() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_PENDING, 0, 3);
        Message sent = Message.builder().deliveryStatus("DELIVERED").build();
        sent.setId(UUID.randomUUID());

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));
        when(outboxService.claimForDispatch(eq(entry.getId()), eq(entry.getAttempts()))).thenReturn(true);
        when(crmService.sendAgentReply(eq(orgId), eq(convoId), any(), any(), eq(false))).thenReturn(sent);

        dispatcher.retryAbandonedDispatches();

        verify(outboxService).claimForDispatch(entry.getId(), entry.getAttempts());
        verify(outboxService).markDispatched(eq(entry.getId()), eq(sent.getId()), any());
    }

    @Test
    @DisplayName("Should not re-dispatch an entry whose attempt budget is exhausted")
    void shouldNotRetryExhaustedEntry() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_FAILED, 3, 3);

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));

        dispatcher.retryAbandonedDispatches();

        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), eq(false));
        verify(outboxService).markFailed(eq(entry.getId()), any());
    }

    @Test
    @DisplayName("Should mark a failed retry as failed")
    void shouldMarkFailedRetry() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_FAILED, 1, 3);
        Message failed = Message.builder().deliveryStatus("FAILED").build();
        failed.setId(UUID.randomUUID());

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));
        when(outboxService.claimForDispatch(eq(entry.getId()), eq(entry.getAttempts()))).thenReturn(true);
        when(crmService.sendAgentReply(eq(orgId), eq(convoId), any(), any(), eq(false))).thenReturn(failed);

        dispatcher.retryAbandonedDispatches();

        verify(outboxService).markFailed(eq(entry.getId()), any());
        verify(outboxService, never()).markDispatched(any(), any(), any());
    }

    @Test
    @DisplayName("Should mark an unconfirmed retry as ambiguous so it is never retried automatically")
    void shouldMarkAmbiguousRetry() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_PENDING, 1, 3);
        Message ambiguous = Message.builder().deliveryStatus("AMBIGUOUS").build();
        ambiguous.setId(UUID.randomUUID());

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));
        when(outboxService.claimForDispatch(eq(entry.getId()), eq(entry.getAttempts()))).thenReturn(true);
        when(crmService.sendAgentReply(eq(orgId), eq(convoId), any(), any(), eq(false))).thenReturn(ambiguous);

        dispatcher.retryAbandonedDispatches();

        verify(outboxService).markAmbiguous(eq(entry.getId()), any());
        verify(outboxService, never()).markDispatched(any(), any(), any());
    }

    @Test
    @DisplayName("Should record a failure when the retry itself raises an error")
    void shouldHandleRetryException() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_PENDING, 1, 3);

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));
        when(outboxService.claimForDispatch(eq(entry.getId()), eq(entry.getAttempts()))).thenReturn(true);
        when(crmService.sendAgentReply(eq(orgId), eq(convoId), any(), any(), eq(false)))
                .thenThrow(new RuntimeException("conversation no longer exists"));

        dispatcher.retryAbandonedDispatches();

        verify(outboxService).markFailed(eq(entry.getId()), any());
    }

    @Test
    @DisplayName("Should not dispatch when the entry was claimed by another worker")
    void shouldNotDispatchWhenClaimLost() {
        CrmOutboxMessage entry = entry(CrmOutboxService.STATUS_PENDING, 0, 3);

        when(outboxService.findRetryableEntries()).thenReturn(List.of(entry));
        when(outboxService.claimForDispatch(eq(entry.getId()), eq(entry.getAttempts()))).thenReturn(false);

        dispatcher.retryAbandonedDispatches();

        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), eq(false));
        verify(outboxService, never()).markDispatched(any(), any(), any());
    }

    @Test
    @DisplayName("Should do nothing when there is nothing to retry")
    void shouldNoOpWhenEmpty() {
        when(outboxService.findRetryableEntries()).thenReturn(List.of());

        dispatcher.retryAbandonedDispatches();

        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), eq(false));
    }

    private CrmOutboxMessage entry(String status, int attempts, int maxAttempts) {
        CrmOutboxMessage entry = CrmOutboxMessage.builder()
                .idempotencyKey("key-" + UUID.randomUUID())
                .status(status)
                .attempts(attempts)
                .maxAttempts(maxAttempts)
                .conversationId(convoId)
                .payloadContent("Hello there")
                .build();
        entry.setId(UUID.randomUUID());
        entry.setOrganizationId(orgId);
        return entry;
    }
}
