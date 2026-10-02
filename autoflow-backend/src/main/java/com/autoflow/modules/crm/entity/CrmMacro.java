package com.autoflow.modules.crm.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "crm_macros")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrmMacro extends TenantAwareEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "actions_json", nullable = false, columnDefinition = "text")
    @Builder.Default
    private String actionsJson = "[]";

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}
