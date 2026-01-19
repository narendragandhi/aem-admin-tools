package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.config.AemConfig;
import com.adobe.aem.admintools.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

/**
 * Content Health Check Tool - Scans content paths for common issues:
 * - Missing required properties
 * - Broken references
 * - Stale content (not modified recently)
 * - Missing alt text on images
 * - etc.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentHealthCheckTool implements AdminTool {

    private final AemConfig aemConfig;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("content-health-check")
                .name("Content Health Check")
                .description("Scans content paths for common issues like missing properties, broken references, and stale content")
                .category("Content")
                .icon("health")
                .destructive(false)
                .requiresAem(false)
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
                                .name("dryRun")
                                .label("Dry Run")
                                .description("Only report issues, don't fix anything")
                                .type(ToolParameter.ParameterType.BOOLEAN)
                                .required(false)
                                .defaultValue(true)
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

        job.addLog(JobLogEntry.Level.INFO, "Starting content health check on: " + rootPath);
        job.addLog(JobLogEntry.Level.INFO, "Running checks: " + String.join(", ", checks));

        List<SimulatedPage> pages = generateSimulatedContent(rootPath);
        job.setTotalItems(pages.size());
        progressCallback.accept(job);

        for (SimulatedPage page : pages) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                job.addLog(JobLogEntry.Level.WARN, "Job cancelled, stopping scan");
                break;
            }

            List<String> issues = new ArrayList<>();

            if (checks.contains("missing-title") && page.title == null) {
                issues.add("Missing page title");
            }
            if (checks.contains("missing-description") && page.description == null) {
                issues.add("Missing meta description");
            }
            if (checks.contains("broken-references") && page.hasBrokenRef) {
                issues.add("Contains broken reference to: " + page.brokenRefPath);
            }
            if (checks.contains("stale-content") && page.daysSinceModified > staleDays) {
                issues.add("Content is stale (last modified " + page.daysSinceModified + " days ago)");
            }
            if (checks.contains("missing-alt-text") && page.imageWithoutAlt) {
                issues.add("Image missing alt text");
            }
            if (checks.contains("unpublished-changes") && page.hasUnpublishedChanges) {
                issues.add("Has unpublished changes");
            }

            JobResult.ResultStatus status;
            String message;

            if (issues.isEmpty()) {
                status = JobResult.ResultStatus.SUCCESS;
                message = "No issues found";
            } else {
                status = JobResult.ResultStatus.ERROR;
                message = String.join("; ", issues);
            }

            job.addResult(JobResult.builder()
                    .path(page.path)
                    .status(status)
                    .message(message)
                    .details(Map.of(
                            "title", page.title != null ? page.title : "",
                            "issues", issues,
                            "lastModified", page.daysSinceModified + " days ago"
                    ))
                    .build());

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            progressCallback.accept(job);
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Scan complete. Checked %d pages. Found %d with issues, %d healthy.",
                job.getTotalItems(), job.getErrorCount(), job.getSuccessCount()));
    }

    private List<SimulatedPage> generateSimulatedContent(String rootPath) {
        Random random = new Random(42);
        List<SimulatedPage> pages = new ArrayList<>();

        String[] sections = {"en", "products", "services", "about", "blog", "contact"};
        String[] pageNames = {"index", "overview", "details", "faq", "pricing", "team", "careers"};

        for (String section : sections) {
            for (int i = 0; i < 3; i++) {
                String pageName = pageNames[random.nextInt(pageNames.length)];
                String path = rootPath + "/" + section + "/" + pageName + "-" + i;

                SimulatedPage page = new SimulatedPage();
                page.path = path;
                page.title = random.nextDouble() > 0.2 ? "Page: " + pageName : null;
                page.description = random.nextDouble() > 0.3 ? "Description for " + pageName : null;
                page.hasBrokenRef = random.nextDouble() > 0.85;
                page.brokenRefPath = "/content/dam/missing-asset-" + random.nextInt(100) + ".jpg";
                page.daysSinceModified = random.nextInt(200);
                page.imageWithoutAlt = random.nextDouble() > 0.7;
                page.hasUnpublishedChanges = random.nextDouble() > 0.6;

                pages.add(page);
            }
        }

        return pages;
    }

    private static class SimulatedPage {
        String path;
        String title;
        String description;
        boolean hasBrokenRef;
        String brokenRefPath;
        int daysSinceModified;
        boolean imageWithoutAlt;
        boolean hasUnpublishedChanges;
    }
}
