package io.cxforge.admintools.filter;

import io.cxforge.admintools.config.RateLimitConfig;
import io.cxforge.admintools.config.RateLimitConfig.BucketType;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.Counter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;
    private final Counter rateLimitRejectedCounter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!rateLimitConfig.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientKey = getClientKey(request);
        BucketType bucketType = determineBucketType(request);
        Bucket bucket = rateLimitConfig.resolveBucket(clientKey + ":" + bucketType.name(), bucketType);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitForRefill = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded. Please try again in %d seconds.",
                        "retryAfterSeconds": %d
                    }
                    """.formatted(waitForRefill, waitForRefill));

            log.warn("Rate limit exceeded for client: {} on endpoint: {}", clientKey, request.getRequestURI());

            // Metrics
            rateLimitRejectedCounter.increment();
        }
    }

    private String getClientKey(HttpServletRequest request) {
        // Try to get the real IP from common proxy headers
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }

        // If multiple IPs in X-Forwarded-For, take the first one
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }

        return clientIp != null ? clientIp : "unknown";
    }

    private BucketType determineBucketType(HttpServletRequest request) {
        // Job creation endpoint gets stricter rate limiting
        String uri = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) &&
            (uri.startsWith("/api/v1/jobs") || uri.startsWith("/api/jobs"))) {
            return BucketType.JOB_CREATION;
        }
        return BucketType.GENERAL;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip rate limiting for health checks and static resources
        return path.startsWith("/actuator/health") ||
               path.startsWith("/actuator/info") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs");
    }
}
