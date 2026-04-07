package io.cxforge.admintools.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Dead Letter Queue entry for failed jobs that exceeded retry attempts.
 */
@Entity
@Table(name = "failed_jobs", indexes = {
    @Index(name = "idx_failed_jobs_tenant", columnList = "tenant_id"),
    @Index(name = "idx_failed_jobs_tool", columnList = "tool_id"),
    @Index(name = "idx_failed_jobs_failed_at", columnList = "failed_at")
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FailedJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "original_job_id", nullable = false)
    private String originalJobId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "tool_id", nullable = false)
    private String toolId;

    @Column(name = "tool_name", nullable = false)
    private String toolName;

    @Column(name = "parameters_json", columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "first_attempted_at", nullable = false)
    private Instant firstAttemptedAt;

    @Column(name = "failed_at", nullable = false)
    private Instant failedAt;

    @Column(name = "retried_at")
    private Instant retriedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "dlq_status", nullable = false)
    @Builder.Default
    private DlqStatus dlqStatus = DlqStatus.PENDING;

    @Column(name = "retry_notes", length = 1000)
    private String retryNotes;

    public enum DlqStatus {
        PENDING,      // Waiting for manual review
        RETRYING,     // Currently being retried
        RESOLVED,     // Successfully retried
        DISCARDED     // Manually discarded
    }
}
