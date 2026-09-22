package com.autoflow.security;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Optional;
import java.util.UUID;

/**
 * ThreadLocal holder for the currently authenticated organization tenant.
 */
@Slf4j
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    public static final String MDC_TENANT_KEY = "tenantId";

    private TenantContext() {
    }

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
        if (tenantId != null) {
            MDC.put(MDC_TENANT_KEY, tenantId.toString());
        } else {
            MDC.remove(MDC_TENANT_KEY);
        }
    }

    public static Optional<UUID> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    public static UUID getRequiredTenantId() {
        return getTenantId().orElseThrow(() ->
                new IllegalStateException("No active tenant context bound to the current thread"));
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        MDC.remove(MDC_TENANT_KEY);
    }
}
