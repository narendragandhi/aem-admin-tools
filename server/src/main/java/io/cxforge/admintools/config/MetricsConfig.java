package io.cxforge.admintools.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom metrics configuration for observability.
 * Exposes metrics via /actuator/prometheus endpoint.
 */
@Configuration
public class MetricsConfig {

    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final AtomicInteger aemCircuitBreakerState = new AtomicInteger(0); // 0=closed, 1=open, 2=half-open

    @Bean
    public AtomicInteger activeJobsGauge() {
        return activeJobs;
    }

    @Bean
    public AtomicInteger aemCircuitBreakerGauge() {
        return aemCircuitBreakerState;
    }

    @Bean
    public Counter jobsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.jobs.created")
                .description("Total number of jobs created")
                .register(registry);
    }

    @Bean
    public Counter jobsCompletedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.jobs.completed")
                .description("Total number of jobs completed successfully")
                .register(registry);
    }

    @Bean
    public Counter jobsFailedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.jobs.failed")
                .description("Total number of jobs that failed")
                .register(registry);
    }

    @Bean
    public Counter jobsCancelledCounter(MeterRegistry registry) {
        return Counter.builder("admintools.jobs.cancelled")
                .description("Total number of jobs cancelled")
                .register(registry);
    }

    @Bean
    public Gauge activeJobsGaugeMetric(MeterRegistry registry) {
        return Gauge.builder("admintools.jobs.active", activeJobs, AtomicInteger::get)
                .description("Number of currently active jobs")
                .register(registry);
    }

    @Bean
    public Timer jobExecutionTimer(MeterRegistry registry) {
        return Timer.builder("admintools.jobs.execution.time")
                .description("Job execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // AEM metrics
    @Bean
    public Counter aemRequestsCounter(MeterRegistry registry) {
        return Counter.builder("admintools.aem.requests")
                .description("Total number of AEM requests")
                .register(registry);
    }

    @Bean
    public Counter aemRequestsFailedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.aem.requests.failed")
                .description("Total number of failed AEM requests")
                .register(registry);
    }

    @Bean
    public Timer aemRequestTimer(MeterRegistry registry) {
        return Timer.builder("admintools.aem.request.time")
                .description("AEM request execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Gauge aemCircuitBreakerGaugeMetric(MeterRegistry registry) {
        return Gauge.builder("admintools.aem.circuitbreaker.state", aemCircuitBreakerState, AtomicInteger::get)
                .description("AEM circuit breaker state (0=closed, 1=open, 2=half-open)")
                .register(registry);
    }

    // LLM metrics
    @Bean
    public Counter llmRequestsCounter(MeterRegistry registry) {
        return Counter.builder("admintools.llm.requests")
                .description("Total number of LLM requests")
                .register(registry);
    }

    @Bean
    public Counter llmRequestsFailedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.llm.requests.failed")
                .description("Total number of failed LLM requests")
                .register(registry);
    }

    @Bean
    public Timer llmRequestTimer(MeterRegistry registry) {
        return Timer.builder("admintools.llm.request.time")
                .description("LLM request execution time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Bean
    public Counter llmTokensUsedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.llm.tokens.used")
                .description("Total tokens used in LLM requests")
                .register(registry);
    }

    // Rate limiting metrics
    @Bean
    public Counter rateLimitRejectedCounter(MeterRegistry registry) {
        return Counter.builder("admintools.ratelimit.rejected")
                .description("Total number of requests rejected by rate limiter")
                .register(registry);
    }
}
