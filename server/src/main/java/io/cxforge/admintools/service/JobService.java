package io.cxforge.admintools.service;

import io.cxforge.admintools.agui.AgUIEvent;
import io.cxforge.admintools.context.TenantContext;
import io.cxforge.admintools.model.*;
import io.cxforge.admintools.repository.FailedJobRepository;
import io.cxforge.admintools.repository.JobRepository;
import io.cxforge.admintools.tool.AdminTool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class JobService {

    private final ToolRegistry toolRegistry;
    private final JobRepository jobRepository;
    private final FailedJobRepository failedJobRepository;
    private final AuditService auditService;
    private final Counter jobsCreatedCounter;
    private final Counter jobsCompletedCounter;
    private final Counter jobsFailedCounter;
    private final Counter jobsCancelledCounter;
    private final Counter jobsRetriedCounter;
    private final AtomicInteger activeJobsGauge;
    private final AtomicInteger dlqSizeGauge;
    private final Timer jobExecutionTimer;

    @Value("${jobs.history-retention-hours:24}")
    private int historyRetentionHours;

    @Value("${jobs.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${jobs.retry.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    // In-memory cache for active jobs (for streaming)
    private final Map<String, Job> activeJobs = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<Job>> jobSinks = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<AgUIEvent>> agUISinks = new ConcurrentHashMap<>();

    // Retry tracking
    private final Map<String, Integer> retryAttempts = new ConcurrentHashMap<>();

    public JobService(ToolRegistry toolRegistry, JobRepository jobRepository,
                      FailedJobRepository failedJobRepository, AuditService auditService,
                      Counter jobsCreatedCounter, Counter jobsCompletedCounter, Counter jobsFailedCounter,
                      Counter jobsCancelledCounter, AtomicInteger activeJobsGauge, Timer jobExecutionTimer,
                      MeterRegistry meterRegistry) {
        this.toolRegistry = toolRegistry;
        this.jobRepository = jobRepository;
        this.failedJobRepository = failedJobRepository;
        this.auditService = auditService;
        this.jobsCreatedCounter = jobsCreatedCounter;
        this.jobsCompletedCounter = jobsCompletedCounter;
        this.jobsFailedCounter = jobsFailedCounter;
        this.jobsCancelledCounter = jobsCancelledCounter;
        this.activeJobsGauge = activeJobsGauge;
        this.jobExecutionTimer = jobExecutionTimer;

        // Additional metrics for retry/DLQ
        this.jobsRetriedCounter = meterRegistry.counter("admintools.jobs.retried.total");
        this.dlqSizeGauge = new AtomicInteger(0);
        Gauge.builder("admintools.jobs.dlq.size", dlqSizeGauge, AtomicInteger::get)
                .description("Number of jobs in dead letter queue")
                .register(meterRegistry);
    }

    @Transactional
    public Job createJob(String toolId, Map<String, Object> parameters) {
        AdminTool tool = toolRegistry.getTool(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolId));

        String validationError = tool.validateParameters(parameters);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        Job job = Job.create(toolId, tool.getDefinition().getName(), parameters);

        // Set tenant if multi-tenancy is enabled
        JobEntity entity = JobEntity.fromJob(job);
        if (multitenancyEnabled) {
            entity.setTenantId(TenantContext.getTenantIdOrDefault("default"));
        }

        // Save to database
        jobRepository.save(entity);

        // Add to active cache for streaming
        activeJobs.put(job.getId(), job);

        Sinks.Many<Job> sink = Sinks.many().multicast().onBackpressureBuffer();
        jobSinks.put(job.getId(), sink);

        Sinks.Many<AgUIEvent> agUISink = Sinks.many().multicast().onBackpressureBuffer();
        agUISinks.put(job.getId(), agUISink);

        // Audit log
        auditService.logJobCreated(job.getId(), toolId, "Parameters: " + parameters.keySet());

        // Metrics
        jobsCreatedCounter.increment();
        activeJobsGauge.incrementAndGet();

        return job;
    }

    @Async
    public void executeJob(String jobId) {
        executeJobWithRetry(jobId, 1);
    }

    private void executeJobWithRetry(String jobId, int attemptNumber) {
        Job job = activeJobs.get(jobId);
        if (job == null) {
            log.error("Job not found in active cache: {}", jobId);
            return;
        }

        AdminTool tool = toolRegistry.getTool(job.getToolId())
                .orElseThrow(() -> new IllegalStateException("Tool not found: " + job.getToolId()));

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.addLog(JobLogEntry.Level.INFO, attemptNumber > 1
                ? String.format("Job started (attempt %d/%d)", attemptNumber, maxRetryAttempts)
                : "Job started");
        emitUpdate(job);
        persistJob(job);
        emitAgUIEvent(jobId, AgUIEvent.runStarted(jobId));
        emitAgUIEvent(jobId, AgUIEvent.toolCallStart(jobId, job.getToolId(), job.getToolName()));

        // Audit log
        auditService.logJobStarted(jobId, job.getToolId());

        Timer.Sample timerSample = Timer.start();
        try {
            // Pass emitUpdate as callback to the tool
            tool.execute(job, this::emitUpdateWithAgUI);

            job.setStatus(JobStatus.COMPLETED);
            job.addLog(JobLogEntry.Level.INFO,
                    String.format("Job completed. Success: %d, Errors: %d, Skipped: %d",
                            job.getSuccessCount(), job.getErrorCount(), job.getSkippedCount()));

            Map<String, Object> result = new HashMap<>();
            result.put("totalItems", job.getTotalItems());
            result.put("successCount", job.getSuccessCount());
            result.put("errorCount", job.getErrorCount());
            result.put("skippedCount", job.getSkippedCount());
            emitAgUIEvent(jobId, AgUIEvent.toolCallEnd(jobId, job.getToolId(), result));
            emitAgUIEvent(jobId, AgUIEvent.runFinished(jobId, result));

            // Audit log
            auditService.logJobCompleted(jobId, job.getResults().size());

            // Metrics
            jobsCompletedCounter.increment();

            // Clear retry tracking on success
            retryAttempts.remove(jobId);

            // Finalize job
            finalizeJob(job, timerSample);
        } catch (Exception e) {
            handleJobFailure(job, e, attemptNumber, timerSample);
        }
    }

    private void handleJobFailure(Job job, Exception e, int attemptNumber, Timer.Sample timerSample) {
        String jobId = job.getId();
        log.error("Job failed (attempt {}/{}): {}", attemptNumber, maxRetryAttempts, jobId, e);

        // Check if we should retry
        if (attemptNumber < maxRetryAttempts && isRetryableException(e)) {
            // Schedule retry with exponential backoff
            long backoffMs = calculateBackoff(attemptNumber);
            job.addLog(JobLogEntry.Level.WARN,
                    String.format("Attempt %d failed: %s. Retrying in %dms...", attemptNumber, e.getMessage(), backoffMs));
            job.setStatus(JobStatus.PENDING);
            emitUpdate(job);
            persistJob(job);

            jobsRetriedCounter.increment();

            // Schedule retry
            scheduleRetry(jobId, attemptNumber + 1, backoffMs);
        } else {
            // Max retries exceeded - move to DLQ
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.addLog(JobLogEntry.Level.ERROR,
                    String.format("Job failed after %d attempts: %s", attemptNumber, e.getMessage()));
            emitAgUIEvent(jobId, AgUIEvent.runError(jobId, e.getMessage()));

            // Move to Dead Letter Queue
            moveToDeadLetterQueue(job, e, attemptNumber);

            // Audit log
            auditService.logJobFailed(jobId, e.getMessage());

            // Metrics
            jobsFailedCounter.increment();

            // Finalize job
            finalizeJob(job, timerSample);
        }
    }

    private void finalizeJob(Job job, Timer.Sample timerSample) {
        job.setCompletedAt(Instant.now());
        emitUpdate(job);
        persistJob(job);
        completeJobStream(job.getId());
        completeAgUIStream(job.getId());
        activeJobs.remove(job.getId());
        retryAttempts.remove(job.getId());

        timerSample.stop(jobExecutionTimer);
        activeJobsGauge.decrementAndGet();
    }

    private boolean isRetryableException(Exception e) {
        // Retry on transient errors (connection issues, timeouts, etc.)
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return message.contains("timeout") ||
               message.contains("connection") ||
               message.contains("unavailable") ||
               message.contains("temporarily") ||
               e instanceof java.net.SocketException ||
               e instanceof java.net.ConnectException ||
               e instanceof java.io.IOException;
    }

    private long calculateBackoff(int attemptNumber) {
        // Exponential backoff: initialBackoff * 2^(attempt-1)
        // With jitter to prevent thundering herd
        double jitter = 0.8 + Math.random() * 0.4; // 0.8 to 1.2
        return (long) (initialBackoffMs * Math.pow(2, attemptNumber - 1) * jitter);
    }

    private void scheduleRetry(String jobId, int nextAttempt, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                executeJobWithRetry(jobId, nextAttempt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Retry interrupted for job: {}", jobId);
            }
        }).start();
    }

    @Transactional
    protected void moveToDeadLetterQueue(Job job, Exception e, int attemptCount) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));

            FailedJob failedJob = FailedJob.builder()
                    .originalJobId(job.getId())
                    .tenantId(multitenancyEnabled ? TenantContext.getTenantIdOrDefault("default") : null)
                    .toolId(job.getToolId())
                    .toolName(job.getToolName())
                    .parametersJson(job.getParameters() != null ? job.getParameters().toString() : null)
                    .errorMessage(e.getMessage())
                    .stackTrace(sw.toString())
                    .attemptCount(attemptCount)
                    .maxAttempts(maxRetryAttempts)
                    .firstAttemptedAt(job.getStartedAt())
                    .failedAt(Instant.now())
                    .dlqStatus(FailedJob.DlqStatus.PENDING)
                    .build();

            failedJobRepository.save(failedJob);
            dlqSizeGauge.incrementAndGet();

            log.warn("Job {} moved to DLQ after {} attempts", job.getId(), attemptCount);
        } catch (Exception dlqException) {
            log.error("Failed to move job {} to DLQ: {}", job.getId(), dlqException.getMessage());
        }
    }

    public Optional<Job> getJob(String jobId) {
        // Check active cache first
        Job activeJob = activeJobs.get(jobId);
        if (activeJob != null) {
            return Optional.of(activeJob);
        }

        // Fall back to database
        return jobRepository.findById(jobId)
                .map(JobEntity::toJob);
    }

    public List<Job> getAllJobs() {
        return jobRepository.findAllOrderByStartedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, 100)
        ).stream().map(JobEntity::toJob).toList();
    }

    public List<Job> getRecentJobs(int limit) {
        return jobRepository.findAllOrderByStartedAtDesc(
                org.springframework.data.domain.PageRequest.of(0, limit)
        ).stream().map(JobEntity::toJob).toList();
    }

    @Transactional
    public void cancelJob(String jobId) {
        Job job = activeJobs.get(jobId);
        if (job != null && job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.CANCELLED);
            job.addLog(JobLogEntry.Level.WARN, "Job cancelled by user");
            job.setCompletedAt(Instant.now());
            emitUpdate(job);
            persistJob(job);
            completeJobStream(jobId);
            activeJobs.remove(jobId);

            // Audit log
            auditService.logJobCancelled(jobId);

            // Metrics
            jobsCancelledCounter.increment();
            activeJobsGauge.decrementAndGet();
        }
    }

    private void persistJob(Job job) {
        try {
            jobRepository.save(JobEntity.fromJob(job));
        } catch (Exception e) {
            log.error("Failed to persist job {}: {}", job.getId(), e.getMessage());
        }
    }

    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupOldJobs() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(historyRetentionHours));
        int deleted = jobRepository.deleteJobsCompletedBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} old jobs completed before {}", deleted, cutoff);
        }
    }

    public Flux<Job> streamJobUpdates(String jobId) {
        Sinks.Many<Job> sink = jobSinks.get(jobId);
        if (sink == null) {
            return Flux.empty();
        }

        Job currentJob = activeJobs.get(jobId);
        Flux<Job> initialState = currentJob != null ? Flux.just(currentJob) : Flux.empty();

        return Flux.concat(initialState, sink.asFlux())
                .timeout(Duration.ofMinutes(30));
    }

    public void emitUpdate(Job job) {
        Sinks.Many<Job> sink = jobSinks.get(job.getId());
        if (sink != null) {
            sink.tryEmitNext(job);
        }
    }

    private void completeJobStream(String jobId) {
        Sinks.Many<Job> sink = jobSinks.get(jobId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    // AG-UI Protocol Support

    public Flux<AgUIEvent> streamAgUIEvents(String jobId) {
        Sinks.Many<AgUIEvent> sink = agUISinks.get(jobId);
        if (sink == null) {
            return Flux.empty();
        }

        return sink.asFlux().timeout(Duration.ofMinutes(30));
    }

    private void emitUpdateWithAgUI(Job job) {
        emitUpdate(job);

        // Emit STATE_DELTA for progress updates
        Map<String, Object> state = new HashMap<>();
        state.put("status", job.getStatus().name());
        state.put("processedItems", job.getProcessedItems());
        state.put("totalItems", job.getTotalItems());
        state.put("successCount", job.getSuccessCount());
        state.put("errorCount", job.getErrorCount());
        state.put("progressPercent", job.getProgressPercent());

        emitAgUIEvent(job.getId(), AgUIEvent.stateDelta(job.getId(), state));
    }

    private void emitAgUIEvent(String jobId, AgUIEvent event) {
        Sinks.Many<AgUIEvent> sink = agUISinks.get(jobId);
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }

    private void completeAgUIStream(String jobId) {
        Sinks.Many<AgUIEvent> sink = agUISinks.get(jobId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
