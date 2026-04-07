package io.cxforge.admintools.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis configuration for distributed state management.
 * Provides distributed locking, rate limiting, and caching.
 */
@Configuration
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true")
@Slf4j
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:2000}")
    private int redisTimeout;

    @Bean
    public RedissonClient redissonClient() {
        log.info("Initializing Redisson client for Redis at {}:{}", redisHost, redisPort);

        Config config = new Config();

        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setTimeout(redisTimeout)
                .setConnectionPoolSize(10)
                .setConnectionMinimumIdleSize(2)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
