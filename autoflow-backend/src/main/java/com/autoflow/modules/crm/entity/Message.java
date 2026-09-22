package com.autoflow.modules.crm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "external_message_id")
    private String externalMessageId;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction; // INBOUND or OUTBOUND

    @Column(name = "sender_type", nullable = false, length = 20)
    private String senderType; // CONTACT, BOT, AGENT

    @Column(name = "message_type", nullable = false, length = 50)
    @Builder.Default
    private String messageType = "TEXT";

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "delivery_status", nullable = false, length = 50)
    @Builder.Default
    private String deliveryStatus = "SENT";

    @CreatedDate
    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;
}
