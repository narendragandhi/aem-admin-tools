package io.cxforge.admintools.controller;

import io.cxforge.admintools.model.Job;
import io.cxforge.admintools.model.JobStatus;
import io.cxforge.admintools.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for JobController.
 * Uses standalone MockMvc setup to avoid loading Spring context.
 */
@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private JobService jobService;

    @InjectMocks
    private JobController jobController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(jobController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    // Exception handler for tests
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        @ExceptionHandler(io.cxforge.admintools.exception.JobNotFoundException.class)
        public ResponseEntity<Map<String, String>> handleJobNotFound(io.cxforge.admintools.exception.JobNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Test
    @DisplayName("POST /api/v1/jobs creates and starts job")
    void testCreateJob() throws Exception {
        Job mockJob = Job.create("test-tool", "Test Tool", Map.of("path", "/content"));
        when(jobService.createJob(eq("test-tool"), anyMap())).thenReturn(mockJob);
        doNothing().when(jobService).executeJob(anyString());

        Map<String, Object> request = Map.of(
                "toolId", "test-tool",
                "parameters", Map.of("path", "/content")
        );

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.toolId", is("test-tool")))
                .andExpect(jsonPath("$.status", is("PENDING")));

        verify(jobService).executeJob(mockJob.getId());
    }

    @Test
    @DisplayName("POST /api/v1/jobs returns 400 for invalid parameters")
    void testCreateJob_invalidParams() throws Exception {
        when(jobService.createJob(anyString(), anyMap()))
                .thenThrow(new IllegalArgumentException("Path is required"));

        Map<String, Object> request = Map.of(
                "toolId", "test-tool",
                "parameters", Map.of()
        );

        mockMvc.perform(post("/api/v1/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns recent jobs")
    void testGetRecentJobs() throws Exception {
        Job job1 = Job.create("tool1", "Tool 1", Map.of());
        job1.setStatus(JobStatus.COMPLETED);

        Job job2 = Job.create("tool2", "Tool 2", Map.of());
        job2.setStatus(JobStatus.RUNNING);

        when(jobService.getRecentJobs(20)).thenReturn(List.of(job1, job2));

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].toolId", is("tool1")))
                .andExpect(jsonPath("$[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$[1].toolId", is("tool2")))
                .andExpect(jsonPath("$[1].status", is("RUNNING")));
    }

    @Test
    @DisplayName("GET /api/v1/jobs returns empty list when no jobs")
    void testGetRecentJobs_empty() throws Exception {
        when(jobService.getRecentJobs(20)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns job details")
    void testGetJobById() throws Exception {
        Job job = Job.create("test-tool", "Test Tool", Map.of("path", "/content"));
        job.setStatus(JobStatus.COMPLETED);
        job.setStartedAt(Instant.now().minusSeconds(60));
        job.setCompletedAt(Instant.now());

        when(jobService.getJob(job.getId())).thenReturn(Optional.of(job));

        mockMvc.perform(get("/api/v1/jobs/" + job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(job.getId())))
                .andExpect(jsonPath("$.toolId", is("test-tool")))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.parameters.path", is("/content")));
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id} returns 404 for unknown job")
    void testGetJobById_notFound() throws Exception {
        when(jobService.getJob("unknown-id")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/jobs/unknown-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/jobs/{id}/cancel cancels running job")
    void testCancelJob() throws Exception {
        Job job = Job.create("test-tool", "Test Tool", Map.of());
        job.setStatus(JobStatus.RUNNING);

        when(jobService.getJob(job.getId())).thenReturn(Optional.of(job));
        doAnswer(invocation -> {
            job.setStatus(JobStatus.CANCELLED);
            return null;
        }).when(jobService).cancelJob(job.getId());

        mockMvc.perform(post("/api/v1/jobs/" + job.getId() + "/cancel"))
                .andExpect(status().isOk());

        verify(jobService).cancelJob(job.getId());
    }

    @Test
    @DisplayName("POST /api/v1/jobs/{id}/cancel returns 404 for unknown job")
    void testCancelJob_notFound() throws Exception {
        when(jobService.getJob("unknown-id")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/jobs/unknown-id/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/jobs/{id}/stream returns SSE stream")
    void testStreamJobUpdates() throws Exception {
        Job job = Job.create("test-tool", "Test Tool", Map.of());

        when(jobService.streamJobUpdates(job.getId()))
                .thenReturn(reactor.core.publisher.Flux.just(job));

        mockMvc.perform(get("/api/v1/jobs/" + job.getId() + "/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk());
    }
}
