package com.autoflow.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Superclass for all multi-tenant entities in AutoFlow.
 * Enforces tenant isolation via organizationId and Hibernate filters.
 */
@Getter
@Setter
@MappedSuperclass
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "organizationId", type = UUID.class)
)
@Filter(
        name = "tenantFilter",
        condition = "organization_id = :organizationId"
)
public abstract class TenantAwareEntity extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;
}
