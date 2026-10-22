package com.autoflow.modules.crm.repository;

import com.autoflow.modules.crm.entity.CrmOutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrmOutboxMessageRepository extends JpaRepository<CrmOutboxMessage, UUID> {

    Optional<CrmOutboxMessage> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    boolean existsByOrganizationIdAndIdempotencyKeyAndStatus(UUID organizationId, String idempotencyKey, String status);

    List<CrmOutboxMessage> findByOrganizationIdAndConversationIdOrderByCreatedAtDesc(UUID organizationId, UUID conversationId);

    /**
     * Entries in the given statuses whose last attempt is older than the cutoff. Used by the retry
     * worker to only pick up entries that were abandoned (for example by a crash mid-dispatch).
     */
    List<CrmOutboxMessage> findByStatusInAndLastAttemptAtBefore(Collection<String> statuses, Instant cutoff);

    /**
     * Atomically claims an outbox entry for dispatch.
     *
     * <p>The update only succeeds when the entry still has the attempt count the caller observed,
     * so exactly one of several concurrent callers can win the claim. This is what prevents two
     * concurrent macro executions from sending the same reply to a customer twice.
     *
     * @return 1 when the claim was won, 0 when another caller claimed it first
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CrmOutboxMessage o set o.attempts = o.attempts + 1, o.lastAttemptAt = :now "
            + "where o.id = :id and o.attempts = :expectedAttempts")
    int claimAttempt(@Param("id") UUID id, @Param("expectedAttempts") int expectedAttempts, @Param("now") Instant now);
}
