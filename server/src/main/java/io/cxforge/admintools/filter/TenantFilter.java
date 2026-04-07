package io.cxforge.admintools.filter;

import io.cxforge.admintools.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to extract and set tenant context from request headers.
 * For non-OAuth2 scenarios or as a fallback when tenant isn't in JWT.
 */
@Component
@Order(2) // After rate limiting, before business logic
@Slf4j
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Value("${multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    @Value("${multitenancy.default-tenant:default}")
    private String defaultTenant;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (multitenancyEnabled) {
                // Only set from header if not already set (e.g., by JWT)
                if (TenantContext.getTenantId().isEmpty()) {
                    String tenantId = extractTenantId(request);
                    TenantContext.setTenantId(tenantId);
                }

                // Add tenant to response headers for debugging
                TenantContext.getTenantId().ifPresent(
                    tenant -> response.addHeader(TENANT_HEADER, tenant)
                );
            }

            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context after request processing
            TenantContext.clear();
        }
    }

    private String extractTenantId(HttpServletRequest request) {
        String tenantId = request.getHeader(TENANT_HEADER);

        if (tenantId == null || tenantId.isBlank()) {
            log.debug("No tenant header found, using default: {}", defaultTenant);
            return defaultTenant;
        }

        // Sanitize tenant ID
        tenantId = tenantId.trim().toLowerCase().replaceAll("[^a-z0-9-_]", "");

        if (tenantId.length() > 64) {
            log.warn("Tenant ID too long, truncating: {}", tenantId);
            tenantId = tenantId.substring(0, 64);
        }

        return tenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip tenant handling for public/system endpoints
        return path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/h2-console");
    }
}
