package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.CrmOutboxMessage;
import com.autoflow.modules.crm.entity.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retry worker for the CRM transactional outbox.
 *
 * <p>Dispatches that were abandoned (for example because the process crashed between registering the
 * outbox entry and completing the provider call) are picked up here and re-sent. Entries whose outcome
 * was {@code AMBIGUOUS} are deliberately never retried: the customer may already have received the
 * message, so a blind retry would risk a duplicate conversation. Those entries stay in the ledger for
 * manual reconciliation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmOutboxDispatcher {

    private final CrmOutboxService outboxService;
    private final CrmService crmService;

    @Scheduled(cron = "${autoflow.crm.outbox.retry-cron:0 */2 * * * *}")
    public void retryAbandonedDispatches() {
        List<CrmOutboxMessage> candidates;
        try {
            candidates = outboxService.findRetryableEntries();
        } catch (Exception e) {
            log.warn("Unable to scan the CRM outbox for retryable dispatches: {}", e.getMessage());
            return;
        }

        if (candidates.isEmpty()) {
            return;
        }

        log.info("CRM outbox retry scan found {} candidate dispatch(es)", candidates.size());

        for (CrmOutboxMessage entry : candidates) {
            if (entry.getAttempts() >= entry.getMaxAttempts()) {
                log.warn("CRM outbox entry [{}] exhausted after {} attempt(s) - no further automatic retry",
                        entry.getId(), entry.getAttempts());
                outboxService.markFailed(entry.getId(), "Maximum dispatch attempts exhausted");
                continue;
            }

            try {
                // Claim the entry atomically so that two workers (or a worker and a live macro
                // execution) can never dispatch the same message to the customer twice.
                if (!outboxService.claimForDispatch(entry.getId(), entry.getAttempts())) {
                    log.debug("CRM outbox entry [{}] was claimed by another dispatcher", entry.getId());
                    continue;
                }
                Message sent = crmService.sendAgentReply(
                        entry.getOrganizationId(),
                        entry.getConversationId(),
                        entry.getPayloadContent(),
                        entry.getMediaUrl(),
                        false);

                if (sent != null && "FAILED".equalsIgnoreCase(sent.getDeliveryStatus())) {
                    outboxService.markFailed(entry.getId(), "Provider delivery failed during retry");
                    log.warn("CRM outbox entry [{}] retry failed to deliver", entry.getId());
                } else if (sent != null && "AMBIGUOUS".equalsIgnoreCase(sent.getDeliveryStatus())) {
                    outboxService.markAmbiguous(entry.getId(), "Provider response unconfirmed during retry");
                    log.warn("CRM outbox entry [{}] retry outcome ambiguous - manual reconciliation required", entry.getId());
                } else {
                    outboxService.markDispatched(entry.getId(),
                            sent != null ? sent.getId() : null,
                            sent != null ? sent.getExternalMessageId() : null);
                    log.info("CRM outbox entry [{}] successfully re-dispatched", entry.getId());
                }
            } catch (Exception e) {
                log.warn("CRM outbox entry [{}] retry raised an error: {}", entry.getId(), e.getMessage());
                outboxService.markFailed(entry.getId(), e.getMessage());
            }
        }
    }
}
