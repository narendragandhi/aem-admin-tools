package io.cxforge.admintools.repository;

import io.cxforge.admintools.model.JobEntity;
import io.cxforge.admintools.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, String> {

    List<JobEntity> findByToolId(String toolId);

    List<JobEntity> findByStatus(JobStatus status);

    List<JobEntity> findByToolIdAndStatus(String toolId, JobStatus status);

    @Query("SELECT j FROM JobEntity j ORDER BY j.startedAt DESC NULLS LAST")
    Page<JobEntity> findAllOrderByStartedAtDesc(Pageable pageable);

    @Query("SELECT j FROM JobEntity j WHERE j.startedAt >= :since ORDER BY j.startedAt DESC")
    List<JobEntity> findJobsSince(@Param("since") Instant since);

    @Query("SELECT j FROM JobEntity j WHERE j.status IN :statuses ORDER BY j.startedAt DESC")
    List<JobEntity> findByStatuses(@Param("statuses") List<JobStatus> statuses);

    @Modifying
    @Query("DELETE FROM JobEntity j WHERE j.completedAt < :before")
    int deleteJobsCompletedBefore(@Param("before") Instant before);

    @Query("SELECT COUNT(j) FROM JobEntity j WHERE j.status = :status")
    long countByStatus(@Param("status") JobStatus status);

    @Query("SELECT j.toolId, COUNT(j) FROM JobEntity j GROUP BY j.toolId")
    List<Object[]> countJobsByTool();
}
