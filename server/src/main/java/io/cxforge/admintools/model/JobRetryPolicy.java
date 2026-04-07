package io.cxforge.admintools.model;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * Retry policy configuration for job execution.
 */
@Data
@Builder
public class JobRetryPolicy {

    @Builder.Default
    private int maxAttempts = 3;

    @Builder.Default
    private Duration initialBackoff = Duration.ofSeconds(1);

    @Builder.Default
    private Duration maxBackoff = Duration.ofMinutes(5);

    @Builder.Default
    private double backoffMultiplier = 2.0;

    @Builder.Default
    private boolean exponentialBackoff = true;

    /**
     * Calculate the backoff duration for a given attempt number.
     * @param attemptNumber 1-based attempt number
     * @return Duration to wait before next attempt
     */
    public Duration calculateBackoff(int attemptNumber) {
        if (attemptNumber <= 1) {
            return initialBackoff;
        }

        if (!exponentialBackoff) {
            return initialBackoff;
        }

        double multiplier = Math.pow(backoffMultiplier, attemptNumber - 1);
        long backoffMillis = (long) (initialBackoff.toMillis() * multiplier);
        Duration calculated = Duration.ofMillis(backoffMillis);

        return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
    }

    /**
     * Check if another retry attempt should be made.
     * @param currentAttempt Current attempt number (1-based)
     * @return true if more attempts are allowed
     */
    public boolean shouldRetry(int currentAttempt) {
        return currentAttempt < maxAttempts;
    }

    /**
     * Default policy with standard settings.
     */
    public static JobRetryPolicy defaultPolicy() {
        return JobRetryPolicy.builder().build();
    }

    /**
     * No retry policy - fail immediately.
     */
    public static JobRetryPolicy noRetry() {
        return JobRetryPolicy.builder()
                .maxAttempts(1)
                .build();
    }

    /**
     * Aggressive retry policy for critical jobs.
     */
    public static JobRetryPolicy aggressive() {
        return JobRetryPolicy.builder()
                .maxAttempts(5)
                .initialBackoff(Duration.ofMillis(500))
                .maxBackoff(Duration.ofMinutes(2))
                .backoffMultiplier(1.5)
                .build();
    }
}
