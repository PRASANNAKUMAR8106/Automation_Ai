package com.autoflow.modules.tenant.entity;

import com.autoflow.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "branding_config", columnDefinition = "jsonb")
    @Builder.Default
    private String brandingConfig = "{}";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
