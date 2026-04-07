package io.cxforge.admintools.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service for distributed locking using Redis/Redisson.
 * Enables horizontal scaling by ensuring only one instance executes critical sections.
 */
@Service
@ConditionalOnBean(RedissonClient.class)
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "admintools:lock:";
    private static final long DEFAULT_WAIT_TIME = 5;
    private static final long DEFAULT_LEASE_TIME = 30;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        log.info("Distributed lock service initialized with Redisson");
    }

    /**
     * Execute a task with a distributed lock.
     * @param lockName Name of the lock (will be prefixed)
     * @param task The task to execute
     * @return Result of the task, or null if lock couldn't be acquired
     */
    public <T> T executeWithLock(String lockName, Supplier<T> task) {
        return executeWithLock(lockName, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME, TimeUnit.SECONDS, task);
    }

    /**
     * Execute a task with a distributed lock and custom timeouts.
     * @param lockName Name of the lock
     * @param waitTime Maximum time to wait for lock
     * @param leaseTime Maximum time to hold lock
     * @param timeUnit Time unit for wait and lease times
     * @param task The task to execute
     * @return Result of the task, or null if lock couldn't be acquired
     */
    public <T> T executeWithLock(String lockName, long waitTime, long leaseTime,
                                  TimeUnit timeUnit, Supplier<T> task) {
        String fullLockName = LOCK_PREFIX + lockName;
        RLock lock = redissonClient.getLock(fullLockName);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            if (acquired) {
                log.debug("Acquired lock: {}", fullLockName);
                try {
                    return task.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        log.debug("Released lock: {}", fullLockName);
                    }
                }
            } else {
                log.warn("Failed to acquire lock: {} (timeout after {}{})",
                        fullLockName, waitTime, timeUnit);
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for lock: {}", fullLockName);
            return null;
        }
    }

    /**
     * Execute a void task with a distributed lock.
     * @param lockName Name of the lock
     * @param task The task to execute
     * @return true if executed, false if lock couldn't be acquired
     */
    public boolean executeWithLock(String lockName, Runnable task) {
        return executeWithLock(lockName, () -> {
            task.run();
            return true;
        }) != null;
    }

    /**
     * Try to acquire a job execution lock.
     * Ensures only one instance executes a specific job.
     * @param jobId The job ID
     * @return true if lock acquired
     */
    public boolean tryLockJob(String jobId) {
        String lockName = "job:" + jobId;
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
        try {
            return lock.tryLock(0, 300, TimeUnit.SECONDS); // 5 minute max job time
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Release a job execution lock.
     * @param jobId The job ID
     */
    public void unlockJob(String jobId) {
        String lockName = LOCK_PREFIX + "job:" + jobId;
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * Acquire a leader election lock for scheduled tasks.
     * @param taskName Name of the scheduled task
     * @return true if this instance is the leader
     */
    public boolean tryBecomeLeader(String taskName) {
        String lockName = "leader:" + taskName;
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockName);
        try {
            return lock.tryLock(0, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Release leader election lock.
     * @param taskName Name of the scheduled task
     */
    public void releaseLeadership(String taskName) {
        String lockName = LOCK_PREFIX + "leader:" + taskName;
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
