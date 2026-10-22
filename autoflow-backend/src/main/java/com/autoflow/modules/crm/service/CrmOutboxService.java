package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.CrmOutboxMessage;
import com.autoflow.modules.crm.repository.CrmOutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional outbox for outbound CRM dispatches.
 *
 * <p>Every state transition is committed in its own transaction ({@link Propagation#REQUIRES_NEW}) so the
 * outbox ledger survives independently of the business transaction that triggered the dispatch. This is
 * what makes the ledger trustworthy in the one scenario that matters: the provider accepted the message
 * but the surrounding business transaction was rolled back afterwards. Without an independently
 * committed ledger the dispatch would leave no trace at all and the next execution would send a
 * duplicate to the customer.
 *
 * <p>Status semantics:
 * <ul>
 *   <li>{@code PENDING}   - registered, dispatch not confirmed yet</li>
 *   <li>{@code DISPATCHED}- provider confirmed delivery</li>
 *   <li>{@code FAILED}    - provider rejected the message, safe to retry</li>
 *   <li>{@code AMBIGUOUS} - provider outcome unknown (timeout/unconfirmed response). Never retried
 *                           automatically because the customer may already have received the message;
 *                           such entries require manual reconciliation.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmOutboxService {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DISPATCHED = "DISPATCHED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_AMBIGUOUS = "AMBIGUOUS";

    private final CrmOutboxMessageRepository outboxRepository;

    /**
     * Registers an outbound dispatch, or returns the already registered entry for the same idempotency key.
     * The entry is committed before the external dispatch is attempted, so a crash in between leaves a
     * PENDING record that the retry worker can pick up.
     *
     * <p>When two executions register the same idempotency key concurrently, the unique constraint
     * lets exactly one of them through. The loser receives a
     * {@link DataIntegrityViolationException} - this transaction is independent of the caller's, so
     * the caller's own transaction is unaffected and it can safely treat the failure as "another
     * execution already owns this dispatch".
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CrmOutboxMessage register(UUID organizationId, UUID conversationId, UUID macroId, String channel,
                                      String recipientId, String payloadContent, String mediaUrl, String idempotencyKey) {
        Optional<CrmOutboxMessage> existing = outboxRepository.findByOrganizationIdAndIdempotencyKey(organizationId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        CrmOutboxMessage outbox = CrmOutboxMessage.builder()
                .conversationId(conversationId)
                .macroId(macroId)
                .idempotencyKey(idempotencyKey)
                .channel(channel != null ? channel : "WHATSAPP")
                .recipientId(recipientId != null ? recipientId : "")
                .payloadContent(payloadContent)
                .mediaUrl(mediaUrl)
                .status(STATUS_PENDING)
                .attempts(0)
                .maxAttempts(3)
                .build();
        outbox.setOrganizationId(organizationId);
        outbox.setLastAttemptAt(Instant.now());

        // Flush eagerly so that a concurrent registration is reported here, while the unique
        // constraint is still the authority on which registration wins.
        return outboxRepository.saveAndFlush(outbox);
    }

    /**
     * Atomically claims an outbox entry for dispatch.
     *
     * <p>Exactly one of several concurrent callers can win the claim, so a message is never sent to
     * a customer twice because two executions raced. The caller must pass the attempt count it
     * observed; if the entry has moved on in the meantime the claim is refused.
     *
     * @return true when this caller won the claim and may dispatch
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimForDispatch(UUID outboxId, int expectedAttempts) {
        return outboxRepository.claimAttempt(outboxId, expectedAttempts, Instant.now()) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markDispatched(UUID outboxId, UUID messageId, String externalMessageId) {
        updateStatus(outboxId, STATUS_DISPATCHED, null, messageId, externalMessageId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID outboxId, String error) {
        updateStatus(outboxId, STATUS_FAILED, error, null, null);
    }

    /**
     * Marks a dispatch whose outcome could not be confirmed. Such entries are never retried automatically.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAmbiguous(UUID outboxId, String error) {
        updateStatus(outboxId, STATUS_AMBIGUOUS, error, null, null);
    }

    private void updateStatus(UUID outboxId, String status, String error, UUID messageId, String externalMessageId) {
        CrmOutboxMessage outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null) {
            log.warn("Outbox entry {} not found while marking it as {}", outboxId, status);
            return;
        }
        outbox.setStatus(status);
        outbox.setLastError(error);
        if (messageId != null) {
            outbox.setMessageId(messageId);
        }
        if (externalMessageId != null) {
            outbox.setExternalMessageId(externalMessageId);
        }
        if (STATUS_DISPATCHED.equals(status)) {
            outbox.setDispatchedAt(Instant.now());
        }
        outboxRepository.save(outbox);
    }

    /**
     * Returns entries that are eligible for an automatic retry: still pending or failed, and not
     * already exhausted.
     */
    @Transactional(readOnly = true)
    public List<CrmOutboxMessage> findRetryableEntries() {
        return outboxRepository.findByStatusInAndLastAttemptAtBefore(
                List.of(STATUS_PENDING, STATUS_FAILED), Instant.now().minusSeconds(120));
    }
}
