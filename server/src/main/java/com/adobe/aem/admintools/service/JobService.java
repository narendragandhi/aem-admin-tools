package com.adobe.aem.admintools.service;

import com.adobe.aem.admintools.model.Job;
import com.adobe.aem.admintools.model.JobLogEntry;
import com.adobe.aem.admintools.model.JobStatus;
import com.adobe.aem.admintools.tool.AdminTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final ToolRegistry toolRegistry;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<Job>> jobSinks = new ConcurrentHashMap<>();

    public Job createJob(String toolId, Map<String, Object> parameters) {
        AdminTool tool = toolRegistry.getTool(toolId)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found: " + toolId));

        String validationError = tool.validateParameters(parameters);
        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        Job job = Job.create(toolId, tool.getDefinition().getName(), parameters);
        jobs.put(job.getId(), job);

        Sinks.Many<Job> sink = Sinks.many().multicast().onBackpressureBuffer();
        jobSinks.put(job.getId(), sink);

        return job;
    }

    @Async
    public void executeJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            log.error("Job not found: {}", jobId);
            return;
        }

        AdminTool tool = toolRegistry.getTool(job.getToolId())
                .orElseThrow(() -> new IllegalStateException("Tool not found: " + job.getToolId()));

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.addLog(JobLogEntry.Level.INFO, "Job started");
        emitUpdate(job);

        try {
            // Pass emitUpdate as callback to the tool
            tool.execute(job, this::emitUpdate);

            job.setStatus(JobStatus.COMPLETED);
            job.addLog(JobLogEntry.Level.INFO,
                    String.format("Job completed. Success: %d, Errors: %d, Skipped: %d",
                            job.getSuccessCount(), job.getErrorCount(), job.getSkippedCount()));
        } catch (Exception e) {
            log.error("Job failed: {}", jobId, e);
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.addLog(JobLogEntry.Level.ERROR, "Job failed: " + e.getMessage());
        } finally {
            job.setCompletedAt(Instant.now());
            emitUpdate(job);
            completeJobStream(jobId);
        }
    }

    public Optional<Job> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public List<Job> getAllJobs() {
        return jobs.values().stream()
                .sorted((a, b) -> {
                    Instant aTime = a.getStartedAt() != null ? a.getStartedAt() : Instant.MIN;
                    Instant bTime = b.getStartedAt() != null ? b.getStartedAt() : Instant.MIN;
                    return bTime.compareTo(aTime);
                })
                .toList();
    }

    public List<Job> getRecentJobs(int limit) {
        return getAllJobs().stream().limit(limit).toList();
    }

    public void cancelJob(String jobId) {
        Job job = jobs.get(jobId);
        if (job != null && job.getStatus() == JobStatus.RUNNING) {
            job.setStatus(JobStatus.CANCELLED);
            job.addLog(JobLogEntry.Level.WARN, "Job cancelled by user");
            job.setCompletedAt(Instant.now());
            emitUpdate(job);
            completeJobStream(jobId);
        }
    }

    public Flux<Job> streamJobUpdates(String jobId) {
        Sinks.Many<Job> sink = jobSinks.get(jobId);
        if (sink == null) {
            return Flux.empty();
        }

        Job currentJob = jobs.get(jobId);
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
}
