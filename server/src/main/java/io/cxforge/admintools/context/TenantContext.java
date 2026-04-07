package io.cxforge.admintools.context;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Thread-local holder for tenant context in multi-tenant operations.
 * Tenant ID is extracted from JWT claims or request headers and made
 * available throughout the request processing chain.
 */
@Slf4j
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
        // Utility class
    }

    /**
     * Set the current tenant ID for this thread.
     * @param tenantId The tenant identifier
     */
    public static void setTenantId(String tenantId) {
        log.debug("Setting tenant context: {}", tenantId);
        CURRENT_TENANT.set(tenantId);
    }

    /**
     * Get the current tenant ID.
     * @return Optional containing tenant ID if set
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(CURRENT_TENANT.get());
    }

    /**
     * Get the current tenant ID or throw if not set.
     * @return The tenant ID
     * @throws IllegalStateException if no tenant context is set
     */
    public static String requireTenantId() {
        return getTenantId()
                .orElseThrow(() -> new IllegalStateException("No tenant context set"));
    }

    /**
     * Get the current tenant ID or return a default value.
     * @param defaultValue Value to return if no tenant is set
     * @return The tenant ID or default
     */
    public static String getTenantIdOrDefault(String defaultValue) {
        return getTenantId().orElse(defaultValue);
    }

    /**
     * Clear the current tenant context.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        log.debug("Clearing tenant context");
        CURRENT_TENANT.remove();
    }

    /**
     * Execute a runnable with a specific tenant context.
     * Context is automatically cleared after execution.
     * @param tenantId The tenant ID to use
     * @param runnable The code to execute
     */
    public static void executeWithTenant(String tenantId, Runnable runnable) {
        String previousTenant = CURRENT_TENANT.get();
        try {
            setTenantId(tenantId);
            runnable.run();
        } finally {
            if (previousTenant != null) {
                setTenantId(previousTenant);
            } else {
                clear();
            }
        }
    }

    /**
     * Execute a supplier with a specific tenant context.
     * Context is automatically cleared after execution.
     * @param tenantId The tenant ID to use
     * @param supplier The code to execute
     * @return The result of the supplier
     */
    public static <T> T executeWithTenant(String tenantId, java.util.function.Supplier<T> supplier) {
        String previousTenant = CURRENT_TENANT.get();
        try {
            setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenant != null) {
                setTenantId(previousTenant);
            } else {
                clear();
            }
        }
    }
}
