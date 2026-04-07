package io.cxforge.admintools.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "jobs")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobEntity {

    private static final ObjectMapper objectMapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Id
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(nullable = false)
    private String toolId;

    @Column(nullable = false)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(columnDefinition = "TEXT")
    private String parametersJson;

    private Instant startedAt;
    private Instant completedAt;

    @Column(nullable = false)
    private int totalItems;

    @Column(nullable = false)
    private int processedItems;

    @Column(nullable = false)
    private int successCount;

    @Column(nullable = false)
    private int errorCount;

    @Column(nullable = false)
    private int skippedCount;

    @Column(columnDefinition = "TEXT")
    private String logsJson;

    @Column(columnDefinition = "TEXT")
    private String resultsJson;

    @Column(length = 2000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public static JobEntity fromJob(Job job) {
        JobEntity entity = JobEntity.builder()
                .id(job.getId())
                .toolId(job.getToolId())
                .toolName(job.getToolName())
                .status(job.getStatus())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .totalItems(job.getTotalItems())
                .processedItems(job.getProcessedItems())
                .successCount(job.getSuccessCount())
                .errorCount(job.getErrorCount())
                .skippedCount(job.getSkippedCount())
                .errorMessage(job.getErrorMessage())
                .build();

        try {
            if (job.getParameters() != null) {
                entity.setParametersJson(objectMapper.writeValueAsString(job.getParameters()));
            }
            if (job.getLogs() != null && !job.getLogs().isEmpty()) {
                entity.setLogsJson(objectMapper.writeValueAsString(job.getLogs()));
            }
            if (job.getResults() != null && !job.getResults().isEmpty()) {
                entity.setResultsJson(objectMapper.writeValueAsString(job.getResults()));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize job data", e);
        }

        return entity;
    }

    public Job toJob() {
        Job job = Job.builder()
                .id(id)
                .toolId(toolId)
                .toolName(toolName)
                .status(status)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .totalItems(totalItems)
                .processedItems(processedItems)
                .successCount(successCount)
                .errorCount(errorCount)
                .skippedCount(skippedCount)
                .errorMessage(errorMessage)
                .logs(new ArrayList<>())
                .results(new ArrayList<>())
                .build();

        try {
            if (parametersJson != null && !parametersJson.isEmpty()) {
                job.setParameters(objectMapper.readValue(parametersJson,
                        new TypeReference<Map<String, Object>>() {}));
            }
            if (logsJson != null && !logsJson.isEmpty()) {
                job.setLogs(objectMapper.readValue(logsJson,
                        new TypeReference<List<JobLogEntry>>() {}));
            }
            if (resultsJson != null && !resultsJson.isEmpty()) {
                job.setResults(objectMapper.readValue(resultsJson,
                        new TypeReference<List<JobResult>>() {}));
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize job data", e);
        }

        return job;
    }
}
