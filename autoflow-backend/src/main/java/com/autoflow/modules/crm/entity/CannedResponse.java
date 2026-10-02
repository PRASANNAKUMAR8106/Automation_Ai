package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "canned_responses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CannedResponse extends TenantAwareEntity {

    @Column(name = "shortcut", nullable = false, length = 50)
    private String shortcut;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "category", nullable = false, length = 50)
    @Builder.Default
    private String category = "GENERAL";

    @Column(name = "is_shared", nullable = false)
    @Builder.Default
    private boolean isShared = true;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = 0;
}
