package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.CrmOutboxMessage;
import com.autoflow.modules.crm.repository.CrmOutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CrmOutboxService Unit Tests")
class CrmOutboxServiceTest {

    @Mock
    private CrmOutboxMessageRepository outboxRepository;

    private CrmOutboxService outboxService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();
    private final UUID macroId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxService = new CrmOutboxService(outboxRepository);
    }

    @Test
    @DisplayName("Should register a pending outbox entry for a new idempotency key")
    void shouldRegisterPendingEntry() {
        when(outboxRepository.findByOrganizationIdAndIdempotencyKey(orgId, "key-1")).thenReturn(Optional.empty());
        when(outboxRepository.saveAndFlush(any(CrmOutboxMessage.class))).thenAnswer(i -> {
            CrmOutboxMessage m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        CrmOutboxMessage entry = outboxService.register(orgId, convoId, macroId, "WHATSAPP", "+15551234567",
                "Hello there", null, "key-1");

        assertThat(entry.getStatus()).isEqualTo(CrmOutboxService.STATUS_PENDING);
        assertThat(entry.getOrganizationId()).isEqualTo(orgId);
        assertThat(entry.getConversationId()).isEqualTo(convoId);
        assertThat(entry.getMacroId()).isEqualTo(macroId);
        assertThat(entry.getPayloadContent()).isEqualTo("Hello there");
        assertThat(entry.getAttempts()).isZero();
        assertThat(entry.getMaxAttempts()).isEqualTo(3);
        assertThat(entry.getLastAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return the already registered entry instead of creating a duplicate")
    void shouldReuseExistingEntry() {
        CrmOutboxMessage existing = CrmOutboxMessage.builder()
                .idempotencyKey("key-1")
                .status(CrmOutboxService.STATUS_DISPATCHED)
                .build();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(orgId);

        when(outboxRepository.findByOrganizationIdAndIdempotencyKey(orgId, "key-1")).thenReturn(Optional.of(existing));

        CrmOutboxMessage entry = outboxService.register(orgId, convoId, macroId, "WHATSAPP", "+15551234567",
                "Hello there", null, "key-1");

        assertThat(entry.getId()).isEqualTo(existing.getId());
        assertThat(entry.getStatus()).isEqualTo(CrmOutboxService.STATUS_DISPATCHED);
        verify(outboxRepository, never()).saveAndFlush(any(CrmOutboxMessage.class));
    }

    @Test
    @DisplayName("Should win the dispatch claim when the entry still has the observed attempt count")
    void shouldClaimDispatch() {
        UUID entryId = UUID.randomUUID();
        when(outboxRepository.claimAttempt(eq(entryId), eq(0), any(Instant.class))).thenReturn(1);

        assertThat(outboxService.claimForDispatch(entryId, 0)).isTrue();
    }

    @Test
    @DisplayName("Should refuse the dispatch claim when a concurrent execution already claimed it")
    void shouldRefuseClaimWhenAlreadyClaimed() {
        UUID entryId = UUID.randomUUID();
        when(outboxRepository.claimAttempt(eq(entryId), eq(0), any(Instant.class))).thenReturn(0);

        assertThat(outboxService.claimForDispatch(entryId, 0)).isFalse();
    }

    @Test
    @DisplayName("Should mark an entry dispatched with the resulting message identifiers")
    void shouldMarkDispatched() {
        CrmOutboxMessage entry = CrmOutboxMessage.builder()
                .idempotencyKey("key-1")
                .status(CrmOutboxService.STATUS_PENDING)
                .build();
        entry.setId(UUID.randomUUID());
        UUID messageId = UUID.randomUUID();

        when(outboxRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(outboxRepository.save(any(CrmOutboxMessage.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.markDispatched(entry.getId(), messageId, "ext-777");

        assertThat(entry.getStatus()).isEqualTo(CrmOutboxService.STATUS_DISPATCHED);
        assertThat(entry.getMessageId()).isEqualTo(messageId);
        assertThat(entry.getExternalMessageId()).isEqualTo("ext-777");
        assertThat(entry.getDispatchedAt()).isNotNull();
        assertThat(entry.getLastError()).isNull();
    }

    @Test
    @DisplayName("Should mark an entry failed with the provider error")
    void shouldMarkFailed() {
        CrmOutboxMessage entry = CrmOutboxMessage.builder()
                .idempotencyKey("key-1")
                .status(CrmOutboxService.STATUS_PENDING)
                .build();
        entry.setId(UUID.randomUUID());

        when(outboxRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(outboxRepository.save(any(CrmOutboxMessage.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.markFailed(entry.getId(), "provider rejected template");

        assertThat(entry.getStatus()).isEqualTo(CrmOutboxService.STATUS_FAILED);
        assertThat(entry.getLastError()).isEqualTo("provider rejected template");
        assertThat(entry.getDispatchedAt()).isNull();
    }

    @Test
    @DisplayName("Should mark an entry ambiguous and never auto-dispatch it again")
    void shouldMarkAmbiguous() {
        CrmOutboxMessage entry = CrmOutboxMessage.builder()
                .idempotencyKey("key-1")
                .status(CrmOutboxService.STATUS_PENDING)
                .build();
        entry.setId(UUID.randomUUID());

        when(outboxRepository.findById(entry.getId())).thenReturn(Optional.of(entry));
        when(outboxRepository.save(any(CrmOutboxMessage.class))).thenAnswer(i -> i.getArgument(0));

        outboxService.markAmbiguous(entry.getId(), "provider response unconfirmed");

        assertThat(entry.getStatus()).isEqualTo(CrmOutboxService.STATUS_AMBIGUOUS);
        assertThat(entry.getLastError()).isEqualTo("provider response unconfirmed");
    }

    @Test
    @DisplayName("Should only surface pending and failed entries as retryable, and only stale ones")
    void shouldFindRetryableEntries() {
        CrmOutboxMessage stale = CrmOutboxMessage.builder()
                .idempotencyKey("key-stale")
                .status(CrmOutboxService.STATUS_PENDING)
                .build();
        stale.setId(UUID.randomUUID());

        when(outboxRepository.findByStatusInAndLastAttemptAtBefore(
                any(List.class), any(Instant.class))).thenReturn(List.of(stale));

        List<CrmOutboxMessage> retryable = outboxService.findRetryableEntries();

        assertThat(retryable).hasSize(1);
        assertThat(retryable.get(0).getId()).isEqualTo(stale.getId());
    }
}
