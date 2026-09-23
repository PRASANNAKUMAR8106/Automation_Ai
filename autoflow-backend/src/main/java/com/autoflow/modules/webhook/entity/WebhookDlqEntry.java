package com.autoflow.modules.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_dlq_entries")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookDlqEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider; // INSTAGRAM, WHATSAPP, TELEGRAM

    @Column(name = "event_id")
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "headers", columnDefinition = "text")
    private String headers;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "DEAD_LETTER"; // DEAD_LETTER, REPLAYED, DISCARDED

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 1;

    @CreatedDate
    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
}
