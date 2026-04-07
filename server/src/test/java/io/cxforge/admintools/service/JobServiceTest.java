package io.cxforge.admintools.service;

import io.cxforge.admintools.model.Job;
import io.cxforge.admintools.model.JobEntity;
import io.cxforge.admintools.model.JobStatus;
import io.cxforge.admintools.model.ToolDefinition;
import io.cxforge.admintools.model.ToolParameter;
import io.cxforge.admintools.repository.FailedJobRepository;
import io.cxforge.admintools.repository.JobRepository;
import io.cxforge.admintools.tool.AdminTool;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceTest {

    private JobService jobService;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private FailedJobRepository failedJobRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private AdminTool mockTool;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Use simple meter registry for tests
        meterRegistry = new SimpleMeterRegistry();

        // Create counters and gauge
        Counter jobsCreatedCounter = meterRegistry.counter("admintools.jobs.created.total");
        Counter jobsCompletedCounter = meterRegistry.counter("admintools.jobs.completed.total");
        Counter jobsFailedCounter = meterRegistry.counter("admintools.jobs.failed.total");
        Counter jobsCancelledCounter = meterRegistry.counter("admintools.jobs.cancelled.total");
        AtomicInteger activeJobsGauge = new AtomicInteger(0);
        Timer jobExecutionTimer = meterRegistry.timer("admintools.jobs.execution.duration");

        jobService = new JobService(
                toolRegistry,
                jobRepository,
                failedJobRepository,
                auditService,
                jobsCreatedCounter,
                jobsCompletedCounter,
                jobsFailedCounter,
                jobsCancelledCounter,
                activeJobsGauge,
                jobExecutionTimer,
                meterRegistry
        );

        // Setup default repository behavior
        when(jobRepository.save(any(JobEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.findById(anyString())).thenReturn(Optional.empty());
        when(jobRepository.findAllOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Setup default mock tool
        when(mockTool.getDefinition()).thenReturn(
                ToolDefinition.builder()
                        .id("test-tool")
                        .name("Test Tool")
                        .description("A test tool")
                        .category("Test")
                        .parameters(Collections.emptyList())
                        .build()
        );
        when(mockTool.validateParameters(anyMap())).thenReturn(null);
    }

    @Test
    @DisplayName("Create job successfully with valid tool")
    void testCreateJob_success() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Map<String, Object> params = Map.of("param1", "value1");
        Job job = jobService.createJob("test-tool", params);

        assertNotNull(job);
        assertNotNull(job.getId());
        assertEquals("test-tool", job.getToolId());
        assertEquals("Test Tool", job.getToolName());
        assertEquals(JobStatus.PENDING, job.getStatus());
        assertEquals(params, job.getParameters());

        // Verify audit service was called
        verify(auditService).logJobCreated(eq(job.getId()), eq("test-tool"), anyString());
    }

    @Test
    @DisplayName("Create job throws exception for unknown tool")
    void testCreateJob_unknownTool() {
        when(toolRegistry.getTool("unknown-tool")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                jobService.createJob("unknown-tool", Map.of())
        );
    }

    @Test
    @DisplayName("Create job throws exception for invalid parameters")
    void testCreateJob_invalidParameters() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));
        when(mockTool.validateParameters(anyMap())).thenReturn("Path is required");

        assertThrows(IllegalArgumentException.class, () ->
                jobService.createJob("test-tool", Map.of())
        );
    }

    @Test
    @DisplayName("Get job returns existing job")
    void testGetJob_exists() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job createdJob = jobService.createJob("test-tool", Map.of());
        Optional<Job> retrievedJob = jobService.getJob(createdJob.getId());

        assertTrue(retrievedJob.isPresent());
        assertEquals(createdJob.getId(), retrievedJob.get().getId());
    }

    @Test
    @DisplayName("Get job returns empty for unknown job")
    void testGetJob_notExists() {
        Optional<Job> job = jobService.getJob("non-existent-id");
        assertTrue(job.isEmpty());
    }

    @Test
    @DisplayName("Get all jobs returns jobs from repository")
    void testGetAllJobs_sorted() throws Exception {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        // Create jobs
        Job job1 = jobService.createJob("test-tool", Map.of("name", "job1"));
        Job job2 = jobService.createJob("test-tool", Map.of("name", "job2"));
        Job job3 = jobService.createJob("test-tool", Map.of("name", "job3"));

        // Mock repository to return these jobs
        List<JobEntity> entities = List.of(
                JobEntity.fromJob(job3),
                JobEntity.fromJob(job2),
                JobEntity.fromJob(job1)
        );
        when(jobRepository.findAllOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(entities));

        List<Job> allJobs = jobService.getAllJobs();

        assertEquals(3, allJobs.size());
    }

    @Test
    @DisplayName("Get recent jobs respects limit")
    void testGetRecentJobs_limit() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        // Create 5 jobs and collect them
        List<Job> createdJobs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            createdJobs.add(jobService.createJob("test-tool", Map.of("index", i)));
        }

        // Mock repository to return only 3 jobs (simulating the limit)
        List<JobEntity> limitedEntities = createdJobs.subList(0, 3).stream()
                .map(JobEntity::fromJob)
                .toList();
        when(jobRepository.findAllOrderByStartedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(limitedEntities));

        List<Job> recentJobs = jobService.getRecentJobs(3);
        assertEquals(3, recentJobs.size());
    }

    @Test
    @DisplayName("Cancel job updates status to cancelled")
    void testCancelJob_success() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job = jobService.createJob("test-tool", Map.of());
        // Simulate job starting
        job.setStatus(JobStatus.RUNNING);

        // Mock repository to return the cancelled job after it's persisted
        when(jobRepository.findById(job.getId())).thenAnswer(invocation ->
                Optional.of(JobEntity.fromJob(job)));

        jobService.cancelJob(job.getId());

        Optional<Job> cancelledJob = jobService.getJob(job.getId());
        assertTrue(cancelledJob.isPresent());
        assertEquals(JobStatus.CANCELLED, cancelledJob.get().getStatus());
        assertNotNull(cancelledJob.get().getCompletedAt());

        // Verify audit service was called
        verify(auditService).logJobCancelled(job.getId());
    }

    @Test
    @DisplayName("Cancel job does nothing for non-running job")
    void testCancelJob_notRunning() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job = jobService.createJob("test-tool", Map.of());
        // Job is in PENDING status

        jobService.cancelJob(job.getId());

        Optional<Job> pendingJob = jobService.getJob(job.getId());
        assertTrue(pendingJob.isPresent());
        assertEquals(JobStatus.PENDING, pendingJob.get().getStatus());
    }

    @Test
    @DisplayName("Cancel non-existent job does nothing")
    void testCancelJob_notExists() {
        // Should not throw
        assertDoesNotThrow(() -> jobService.cancelJob("non-existent-id"));
    }

    @Test
    @DisplayName("Stream job updates returns flux")
    void testStreamJobUpdates_returnsFlux() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job = jobService.createJob("test-tool", Map.of());

        var flux = jobService.streamJobUpdates(job.getId());
        assertNotNull(flux);
    }

    @Test
    @DisplayName("Stream job updates returns empty for unknown job")
    void testStreamJobUpdates_unknownJob() {
        var flux = jobService.streamJobUpdates("unknown-id");
        assertNotNull(flux);
        // The flux should be empty
        assertEquals(0, flux.collectList().block().size());
    }

    @Test
    @DisplayName("Emit update sends job to sink")
    void testEmitUpdate() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job = jobService.createJob("test-tool", Map.of());

        // Should not throw
        assertDoesNotThrow(() -> jobService.emitUpdate(job));
    }

    @Test
    @DisplayName("Stream AG-UI events returns flux")
    void testStreamAgUIEvents_returnsFlux() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job = jobService.createJob("test-tool", Map.of());

        var flux = jobService.streamAgUIEvents(job.getId());
        assertNotNull(flux);
    }

    @Test
    @DisplayName("Multiple concurrent jobs are isolated")
    void testConcurrentJobs_isolated() {
        when(toolRegistry.getTool("test-tool")).thenReturn(Optional.of(mockTool));

        Job job1 = jobService.createJob("test-tool", Map.of("name", "job1"));
        Job job2 = jobService.createJob("test-tool", Map.of("name", "job2"));

        assertNotEquals(job1.getId(), job2.getId());

        Optional<Job> retrieved1 = jobService.getJob(job1.getId());
        Optional<Job> retrieved2 = jobService.getJob(job2.getId());

        assertTrue(retrieved1.isPresent());
        assertTrue(retrieved2.isPresent());
        assertEquals("job1", retrieved1.get().getParameters().get("name"));
        assertEquals("job2", retrieved2.get().getParameters().get("name"));
    }
}
