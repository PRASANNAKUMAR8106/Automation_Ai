package com.autoflow.modules.workflow.entity;

import com.autoflow.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "workflows")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workflow extends TenantAwareEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, PUBLISHED, PAUSED

    @Column(name = "active_version_number")
    private Integer activeVersionNumber;
}
