package io.cxforge.admintools.repository;

import io.cxforge.admintools.model.AuditLog;
import io.cxforge.admintools.model.AuditLog.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    Page<AuditLog> findByActionOrderByTimestampDesc(AuditAction action, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByTimestampDesc(
            String resourceType, String resourceId, Pageable pageable);

    Page<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            Instant start, Instant end, Pageable pageable);

    List<AuditLog> findByResourceIdOrderByTimestampDesc(String resourceId);

    @Query("SELECT a FROM AuditLog a WHERE a.timestamp >= :since ORDER BY a.timestamp DESC")
    Page<AuditLog> findRecentLogs(Instant since, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :before")
    int deleteLogsOlderThan(Instant before);

    long countByActionAndTimestampAfter(AuditAction action, Instant since);
}
