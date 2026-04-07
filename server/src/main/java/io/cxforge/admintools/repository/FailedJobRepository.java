package io.cxforge.admintools.repository;

import io.cxforge.admintools.model.FailedJob;
import io.cxforge.admintools.model.FailedJob.DlqStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Dead Letter Queue (failed jobs that exceeded retry attempts).
 */
@Repository
public interface FailedJobRepository extends JpaRepository<FailedJob, String> {

    /**
     * Find failed jobs by tenant.
     */
    Page<FailedJob> findByTenantId(String tenantId, Pageable pageable);

    /**
     * Find failed jobs by status.
     */
    Page<FailedJob> findByDlqStatus(DlqStatus status, Pageable pageable);

    /**
     * Find failed jobs by tenant and status.
     */
    Page<FailedJob> findByTenantIdAndDlqStatus(String tenantId, DlqStatus status, Pageable pageable);

    /**
     * Find by original job ID.
     */
    Optional<FailedJob> findByOriginalJobId(String originalJobId);

    /**
     * Find pending jobs for a specific tool (for bulk retry).
     */
    List<FailedJob> findByToolIdAndDlqStatus(String toolId, DlqStatus status);

    /**
     * Count pending failures by tenant.
     */
    @Query("SELECT COUNT(f) FROM FailedJob f WHERE f.tenantId = :tenantId AND f.dlqStatus = 'PENDING'")
    long countPendingByTenant(@Param("tenantId") String tenantId);

    /**
     * Count all pending failures.
     */
    long countByDlqStatus(DlqStatus status);

    /**
     * Find oldest pending failures for alerting.
     */
    @Query("SELECT f FROM FailedJob f WHERE f.dlqStatus = 'PENDING' ORDER BY f.failedAt ASC")
    Page<FailedJob> findOldestPending(Pageable pageable);

    /**
     * Update status for retry.
     */
    @Modifying
    @Query("UPDATE FailedJob f SET f.dlqStatus = :status, f.retriedAt = :retriedAt, f.retryNotes = :notes WHERE f.id = :id")
    int updateStatusForRetry(@Param("id") String id,
                             @Param("status") DlqStatus status,
                             @Param("retriedAt") Instant retriedAt,
                             @Param("notes") String notes);

    /**
     * Bulk discard old failures.
     */
    @Modifying
    @Query("UPDATE FailedJob f SET f.dlqStatus = 'DISCARDED', f.retryNotes = :reason WHERE f.dlqStatus = 'PENDING' AND f.failedAt < :before")
    int discardOlderThan(@Param("before") Instant before, @Param("reason") String reason);

    /**
     * Statistics: count by tool.
     */
    @Query("SELECT f.toolId, COUNT(f) FROM FailedJob f WHERE f.dlqStatus = 'PENDING' GROUP BY f.toolId")
    List<Object[]> countPendingByTool();

    /**
     * Statistics: count by tenant.
     */
    @Query("SELECT f.tenantId, COUNT(f) FROM FailedJob f WHERE f.dlqStatus = 'PENDING' GROUP BY f.tenantId")
    List<Object[]> countPendingByTenant();
}
