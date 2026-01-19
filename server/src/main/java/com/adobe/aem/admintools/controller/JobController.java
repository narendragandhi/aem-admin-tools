package com.adobe.aem.admintools.controller;

import com.adobe.aem.admintools.model.Job;
import com.adobe.aem.admintools.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<Job> createAndStartJob(@RequestBody CreateJobRequest request) {
        Job job = jobService.createJob(request.toolId(), request.parameters());
        jobService.executeJob(job.getId());
        return ResponseEntity.ok(job);
    }

    @GetMapping
    public List<Job> getAllJobs(@RequestParam(defaultValue = "20") int limit) {
        return jobService.getRecentJobs(limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Job> getJob(@PathVariable String id) {
        return jobService.getJob(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelJob(@PathVariable String id) {
        jobService.cancelJob(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Job> streamJob(@PathVariable String id) {
        return jobService.streamJobUpdates(id);
    }

    public record CreateJobRequest(String toolId, Map<String, Object> parameters) {}
}
