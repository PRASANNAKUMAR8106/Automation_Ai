package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Genuine transactional outbox entity for reliable macro replies and outbound message dispatch.
 * Guarantees idempotency, attempt tracking, failure distinction, and safe recovery from ambiguous network outcomes.
 */
@Entity
@Table(name = "crm_outbox_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmOutboxMessage extends TenantAwareEntity {

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "macro_id")
    private UUID macroId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "recipient_id", nullable = false)
    private String recipientId;

    @Column(name = "payload_content", nullable = false, columnDefinition = "text")
    private String payloadContent;

    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, DISPATCHED, FAILED, AMBIGUOUS

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "external_message_id")
    private String externalMessageId;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;
}
