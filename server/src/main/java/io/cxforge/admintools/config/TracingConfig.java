package io.cxforge.admintools.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Configuration for distributed tracing with OpenTelemetry.
 */
@Configuration
@Slf4j
public class TracingConfig {

    /**
     * Enable @Observed annotation support for custom spans.
     */
    @Bean
    @ConditionalOnProperty(name = "management.tracing.enabled", havingValue = "true")
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.info("Enabling distributed tracing with ObservedAspect");
        return new ObservedAspect(observationRegistry);
    }

    /**
     * Filter to add trace IDs to MDC for structured logging and response headers.
     */
    @Component
    @Order(0) // First filter - before everything else
    public static class TraceIdFilter extends OncePerRequestFilter {

        private static final String TRACE_ID_HEADER = "X-Trace-Id";
        private static final String SPAN_ID_HEADER = "X-Span-Id";
        private static final String MDC_TRACE_ID = "traceId";
        private static final String MDC_SPAN_ID = "spanId";

        @Value("${management.tracing.enabled:false}")
        private boolean tracingEnabled;

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            try {
                // Get trace context from W3C traceparent header or generate one
                String traceId = extractOrGenerateTraceId(request);
                String spanId = generateSpanId();

                // Set in MDC for structured logging
                MDC.put(MDC_TRACE_ID, traceId);
                MDC.put(MDC_SPAN_ID, spanId);

                // Add to response headers for client correlation
                response.addHeader(TRACE_ID_HEADER, traceId);
                response.addHeader(SPAN_ID_HEADER, spanId);

                filterChain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_TRACE_ID);
                MDC.remove(MDC_SPAN_ID);
            }
        }

        private String extractOrGenerateTraceId(HttpServletRequest request) {
            // Try W3C traceparent header: version-traceid-parentid-flags
            String traceparent = request.getHeader("traceparent");
            if (traceparent != null && traceparent.length() >= 55) {
                try {
                    String[] parts = traceparent.split("-");
                    if (parts.length >= 2) {
                        return parts[1]; // 32-char trace ID
                    }
                } catch (Exception e) {
                    log.debug("Failed to parse traceparent header: {}", traceparent);
                }
            }

            // Try X-Trace-Id header
            String xTraceId = request.getHeader(TRACE_ID_HEADER);
            if (xTraceId != null && !xTraceId.isBlank()) {
                return xTraceId.trim();
            }

            // Generate new trace ID (32 hex chars like OTel)
            return generateTraceId();
        }

        private String generateTraceId() {
            return String.format("%016x%016x",
                    System.currentTimeMillis(),
                    (long) (Math.random() * Long.MAX_VALUE));
        }

        private String generateSpanId() {
            return String.format("%016x", (long) (Math.random() * Long.MAX_VALUE));
        }
    }
}
