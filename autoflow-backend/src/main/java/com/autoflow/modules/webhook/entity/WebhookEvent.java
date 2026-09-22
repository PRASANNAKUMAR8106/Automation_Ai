package com.autoflow.modules.webhook.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_provider_event", columnNames = {"provider", "event_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreatedDate
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
