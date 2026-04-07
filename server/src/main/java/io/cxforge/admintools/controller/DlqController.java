package io.cxforge.admintools.controller;

import io.cxforge.admintools.context.TenantContext;
import io.cxforge.admintools.model.FailedJob;
import io.cxforge.admintools.model.FailedJob.DlqStatus;
import io.cxforge.admintools.repository.FailedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Dead Letter Queue operations.
 * Allows viewing and managing failed jobs.
 */
@RestController
@RequestMapping("/api/v1/jobs/dlq")
@RequiredArgsConstructor
@Slf4j
public class DlqController {

    private final FailedJobRepository failedJobRepository;

    @Value("${multitenancy.enabled:false}")
    private boolean multitenancyEnabled;

    /**
     * List failed jobs in the DLQ.
     */
    @GetMapping
    public ResponseEntity<Page<FailedJob>> listFailedJobs(
            @RequestParam(defaultValue = "PENDING") DlqStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "failedAt"));

        Page<FailedJob> failedJobs;
        if (multitenancyEnabled) {
            String tenantId = TenantContext.getTenantIdOrDefault("default");
            failedJobs = failedJobRepository.findByTenantIdAndDlqStatus(tenantId, status, pageRequest);
        } else {
            failedJobs = failedJobRepository.findByDlqStatus(status, pageRequest);
        }

        return ResponseEntity.ok(failedJobs);
    }

    /**
     * Get a specific failed job by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<FailedJob> getFailedJob(@PathVariable String id) {
        return failedJobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get DLQ statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long pendingCount = failedJobRepository.countByDlqStatus(DlqStatus.PENDING);
        long retryingCount = failedJobRepository.countByDlqStatus(DlqStatus.RETRYING);
        long resolvedCount = failedJobRepository.countByDlqStatus(DlqStatus.RESOLVED);
        long discardedCount = failedJobRepository.countByDlqStatus(DlqStatus.DISCARDED);

        List<Object[]> byTool = failedJobRepository.countPendingByTool();
        Map<String, Long> pendingByTool = byTool.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));

        return ResponseEntity.ok(Map.of(
                "pending", pendingCount,
                "retrying", retryingCount,
                "resolved", resolvedCount,
                "discarded", discardedCount,
                "pendingByTool", pendingByTool
        ));
    }

    /**
     * Mark a failed job for retry.
     */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, String>> retryFailedJob(
            @PathVariable String id,
            @RequestBody(required = false) RetryRequest request) {

        return failedJobRepository.findById(id)
                .map(failedJob -> {
                    if (failedJob.getDlqStatus() != DlqStatus.PENDING) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Can only retry PENDING jobs"));
                    }

                    String notes = request != null ? request.notes() : null;
                    failedJobRepository.updateStatusForRetry(id, DlqStatus.RETRYING, Instant.now(), notes);

                    // TODO: Actually trigger re-execution via JobService
                    log.info("Marked failed job {} for retry", id);

                    return ResponseEntity.ok(Map.of(
                            "status", "RETRYING",
                            "message", "Job marked for retry"
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Discard a failed job (won't be retried).
     */
    @PostMapping("/{id}/discard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> discardFailedJob(
            @PathVariable String id,
            @RequestBody(required = false) DiscardRequest request) {

        return failedJobRepository.findById(id)
                .map(failedJob -> {
                    if (failedJob.getDlqStatus() != DlqStatus.PENDING) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", "Can only discard PENDING jobs"));
                    }

                    String reason = request != null ? request.reason() : "Manually discarded";
                    failedJobRepository.updateStatusForRetry(id, DlqStatus.DISCARDED, Instant.now(), reason);

                    log.info("Discarded failed job {}: {}", id, reason);

                    return ResponseEntity.ok(Map.of(
                            "status", "DISCARDED",
                            "message", "Job discarded"
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Bulk discard old failed jobs.
     */
    @PostMapping("/discard-old")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> discardOldJobs(
            @RequestParam(defaultValue = "168") int olderThanHours,
            @RequestBody(required = false) DiscardRequest request) {

        Instant cutoff = Instant.now().minusSeconds(olderThanHours * 3600L);
        String reason = request != null ? request.reason() : "Auto-discarded (older than " + olderThanHours + " hours)";

        int discarded = failedJobRepository.discardOlderThan(cutoff, reason);

        log.info("Bulk discarded {} failed jobs older than {}", discarded, cutoff);

        return ResponseEntity.ok(Map.of(
                "discarded", discarded,
                "cutoff", cutoff.toString()
        ));
    }

    public record RetryRequest(String notes) {}
    public record DiscardRequest(String reason) {}
}
