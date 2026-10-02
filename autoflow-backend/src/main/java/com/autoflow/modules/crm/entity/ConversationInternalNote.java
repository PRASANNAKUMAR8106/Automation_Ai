package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "conversation_internal_notes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationInternalNote extends TenantAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "note_type", nullable = false, length = 30)
    @Builder.Default
    private String noteType = "INTERNAL_NOTE"; // INTERNAL_NOTE, SUPERVISOR_WHISPER

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;
}
