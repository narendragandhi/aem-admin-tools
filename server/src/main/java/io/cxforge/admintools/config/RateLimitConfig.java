package io.cxforge.admintools.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitConfig {

    private boolean enabled = true;
    private int requestsPerMinute = 100;
    private int jobsPerMinute = 10;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key, BucketType type) {
        return buckets.computeIfAbsent(key, k -> createBucket(type));
    }

    private Bucket createBucket(BucketType type) {
        int capacity = switch (type) {
            case GENERAL -> requestsPerMinute;
            case JOB_CREATION -> jobsPerMinute;
        };

        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(capacity, Duration.ofMinutes(1))
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    public enum BucketType {
        GENERAL,
        JOB_CREATION
    }
}
