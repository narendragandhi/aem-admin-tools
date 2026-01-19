package com.adobe.aem.admintools.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Job {
    private String id;
    private String toolId;
    private String toolName;
    private JobStatus status;
    private Map<String, Object> parameters;
    private Instant startedAt;
    private Instant completedAt;
    private int totalItems;
    private int processedItems;
    private int successCount;
    private int errorCount;
    private int skippedCount;

    @Builder.Default
    private List<JobLogEntry> logs = new CopyOnWriteArrayList<>();

    @Builder.Default
    private List<JobResult> results = new ArrayList<>();

    private String errorMessage;

    public static Job create(String toolId, String toolName, Map<String, Object> parameters) {
        return Job.builder()
                .id(UUID.randomUUID().toString())
                .toolId(toolId)
                .toolName(toolName)
                .status(JobStatus.PENDING)
                .parameters(parameters)
                .build();
    }

    public double getProgressPercent() {
        if (totalItems == 0) return 0;
        return (double) processedItems / totalItems * 100;
    }

    public void addLog(JobLogEntry.Level level, String message) {
        logs.add(JobLogEntry.builder()
                .timestamp(Instant.now())
                .level(level)
                .message(message)
                .build());
    }

    public void addResult(JobResult result) {
        results.add(result);
        processedItems++;
        switch (result.getStatus()) {
            case SUCCESS -> successCount++;
            case ERROR -> errorCount++;
            case SKIPPED -> skippedCount++;
        }
    }
}
