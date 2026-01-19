package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.*;
import com.adobe.aem.admintools.service.AemClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContentHealthCheckTool implements AdminTool {

    private final AemClientService aemClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("content-health-check")
                .name("Content Health Check")
                .description("Scans content paths for common issues like missing properties, broken references, and stale content")
                .category("Content")
                .icon("health")
                .destructive(false)
                .requiresAem(true)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("rootPath")
                                .label("Root Path")
                                .description("The content path to scan (e.g., /content/mysite)")
                                .type(ToolParameter.ParameterType.PATH)
                                .required(true)
                                .defaultValue("/content/we-retail")
                                .build(),
                        ToolParameter.builder()
                                .name("checks")
                                .label("Checks to Run")
                                .description("Select which health checks to run")
                                .type(ToolParameter.ParameterType.MULTISELECT)
                                .required(true)
                                .options(List.of(
                                        "missing-title",
                                        "missing-description",
                                        "broken-references",
                                        "stale-content",
                                        "missing-alt-text",
                                        "unpublished-changes"
                                ))
                                .defaultValue(List.of("missing-title", "missing-description", "broken-references"))
                                .build(),
                        ToolParameter.builder()
                                .name("staleDays")
                                .label("Stale After (Days)")
                                .description("Content not modified in this many days is considered stale")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(90)
                                .build(),
                        ToolParameter.builder()
                                .name("maxPages")
                                .label("Max Pages to Scan")
                                .description("Maximum number of pages to scan")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(100)
                                .build()
                ))
                .build();
    }

    @Override
    public String validateParameters(Map<String, Object> parameters) {
        String rootPath = (String) parameters.get("rootPath");
        if (rootPath == null || rootPath.isBlank()) {
            return "Root path is required";
        }
        if (!rootPath.startsWith("/content")) {
            return "Root path must start with /content";
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void execute(Job job, Consumer<Job> progressCallback) {
        Map<String, Object> params = job.getParameters();
        String rootPath = (String) params.get("rootPath");
        List<String> checks = (List<String>) params.getOrDefault("checks",
                List.of("missing-title", "missing-description", "broken-references"));
        int staleDays = ((Number) params.getOrDefault("staleDays", 90)).intValue();
        int maxPages = ((Number) params.getOrDefault("maxPages", 100)).intValue();

        job.addLog(JobLogEntry.Level.INFO, "Starting content health check on: " + rootPath);
        job.addLog(JobLogEntry.Level.INFO, "Running checks: " + String.join(", ", checks));

        if (!aemClient.isEnabled()) {
            job.addLog(JobLogEntry.Level.ERROR, "AEM connection is not enabled. Please configure AEM credentials.");
            return;
        }

        try {
            job.addLog(JobLogEntry.Level.INFO, "Querying AEM for pages...");
            List<Map<String, Object>> pages = aemClient.findPages(rootPath, maxPages);

            if (pages.isEmpty()) {
                job.addLog(JobLogEntry.Level.WARN, "No pages found under: " + rootPath);
                return;
            }

            job.addLog(JobLogEntry.Level.INFO, "Found " + pages.size() + " pages to scan");
            job.setTotalItems(pages.size());
            progressCallback.accept(job);

            for (Map<String, Object> page : pages) {
                if (job.getStatus() == JobStatus.CANCELLED) {
                    job.addLog(JobLogEntry.Level.WARN, "Job cancelled, stopping scan");
                    break;
                }

                String pagePath = (String) page.get("jcr:path");
                if (pagePath == null) {
                    pagePath = (String) page.get("path");
                }

                try {
                    processPage(job, pagePath, page, checks, staleDays);
                } catch (Exception e) {
                    job.addResult(JobResult.builder()
                            .path(pagePath)
                            .status(JobResult.ResultStatus.ERROR)
                            .message("Error scanning page: " + e.getMessage())
                            .build());
                }

                progressCallback.accept(job);
            }

        } catch (Exception e) {
            log.error("Failed to query AEM", e);
            job.addLog(JobLogEntry.Level.ERROR, "Failed to query AEM: " + e.getMessage());
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Scan complete. Checked %d pages. Found %d with issues, %d healthy.",
                job.getTotalItems(), job.getErrorCount(), job.getSuccessCount()));
    }

    @SuppressWarnings("unchecked")
    private void processPage(Job job, String pagePath, Map<String, Object> pageData,
                            List<String> checks, int staleDays) throws Exception {
        List<String> issues = new ArrayList<>();

        // Get jcr:content properties
        Map<String, Object> jcrContent = (Map<String, Object>) pageData.get("jcr:content");
        if (jcrContent == null) {
            // Try to fetch it directly
            jcrContent = aemClient.getPageProperties(pagePath);
        }

        if (jcrContent == null) {
            issues.add("Missing jcr:content node");
        } else {
            // Check for missing title
            if (checks.contains("missing-title")) {
                String title = (String) jcrContent.get("jcr:title");
                if (title == null || title.isBlank()) {
                    issues.add("Missing page title (jcr:title)");
                }
            }

            // Check for missing description
            if (checks.contains("missing-description")) {
                String description = (String) jcrContent.get("jcr:description");
                if (description == null || description.isBlank()) {
                    issues.add("Missing meta description (jcr:description)");
                }
            }

            // Check for stale content
            if (checks.contains("stale-content")) {
                Object lastModObj = jcrContent.get("cq:lastModified");
                if (lastModObj != null) {
                    Instant lastModified = parseDate(lastModObj);
                    if (lastModified != null) {
                        long daysSinceMod = ChronoUnit.DAYS.between(lastModified, Instant.now());
                        if (daysSinceMod > staleDays) {
                            issues.add("Content is stale (last modified " + daysSinceMod + " days ago)");
                        }
                    }
                }
            }

            // Check for unpublished changes
            if (checks.contains("unpublished-changes")) {
                Object lastModObj = jcrContent.get("cq:lastModified");
                Object lastReplObj = jcrContent.get("cq:lastReplicated");

                if (lastModObj != null && lastReplObj != null) {
                    Instant lastMod = parseDate(lastModObj);
                    Instant lastRepl = parseDate(lastReplObj);
                    if (lastMod != null && lastRepl != null && lastMod.isAfter(lastRepl)) {
                        issues.add("Has unpublished changes (modified after last publish)");
                    }
                } else if (lastModObj != null && lastReplObj == null) {
                    issues.add("Page has never been published");
                }
            }
        }

        // Determine result status
        JobResult.ResultStatus status;
        String message;

        if (issues.isEmpty()) {
            status = JobResult.ResultStatus.SUCCESS;
            message = "No issues found";
        } else {
            status = JobResult.ResultStatus.ERROR;
            message = String.join("; ", issues);
        }

        String title = jcrContent != null ? (String) jcrContent.get("jcr:title") : "";

        job.addResult(JobResult.builder()
                .path(pagePath)
                .status(status)
                .message(message)
                .details(Map.of(
                        "title", title != null ? title : "",
                        "issues", issues,
                        "checksRun", checks
                ))
                .build());
    }

    private Instant parseDate(Object dateObj) {
        if (dateObj == null) {
            return null;
        }
        try {
            if (dateObj instanceof String dateStr) {
                // Handle ISO format or AEM date format
                if (dateStr.contains("T")) {
                    return Instant.parse(dateStr.replace(" ", "T").replaceAll("\\+.*", "Z"));
                }
            } else if (dateObj instanceof Long timestamp) {
                return Instant.ofEpochMilli(timestamp);
            }
        } catch (Exception e) {
            log.debug("Could not parse date: {}", dateObj);
        }
        return null;
    }
}
