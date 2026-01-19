package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.config.AemConfig;
import com.adobe.aem.admintools.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkTagManagerTool implements AdminTool {

    private final AemConfig aemConfig;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("bulk-tag-manager")
                .name("Bulk Tag Manager")
                .description("Add, remove, or replace tags across multiple content pages")
                .category("Content")
                .icon("tag")
                .destructive(true)
                .requiresAem(false)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("operation")
                                .label("Operation")
                                .description("The tag operation to perform")
                                .type(ToolParameter.ParameterType.SELECT)
                                .required(true)
                                .options(List.of("add", "remove", "replace", "find"))
                                .defaultValue("find")
                                .build(),
                        ToolParameter.builder()
                                .name("rootPath")
                                .label("Root Path")
                                .description("The content path to scan")
                                .type(ToolParameter.ParameterType.PATH)
                                .required(true)
                                .defaultValue("/content/we-retail")
                                .build(),
                        ToolParameter.builder()
                                .name("sourceTag")
                                .label("Source Tag")
                                .description("The tag to find/remove/replace")
                                .type(ToolParameter.ParameterType.STRING)
                                .required(true)
                                .defaultValue("we-retail:activity/hiking")
                                .build(),
                        ToolParameter.builder()
                                .name("targetTag")
                                .label("Target Tag")
                                .description("The tag to add/replace with (for add/replace operations)")
                                .type(ToolParameter.ParameterType.STRING)
                                .required(false)
                                .build(),
                        ToolParameter.builder()
                                .name("dryRun")
                                .label("Dry Run")
                                .description("Preview changes without making them")
                                .type(ToolParameter.ParameterType.BOOLEAN)
                                .required(false)
                                .defaultValue(true)
                                .build()
                ))
                .build();
    }

    @Override
    public String validateParameters(Map<String, Object> parameters) {
        String operation = (String) parameters.get("operation");
        String sourceTag = (String) parameters.get("sourceTag");
        String targetTag = (String) parameters.get("targetTag");

        if (operation == null || operation.isBlank()) {
            return "Operation is required";
        }

        if (sourceTag == null || sourceTag.isBlank()) {
            return "Source tag is required";
        }

        if (("add".equals(operation) || "replace".equals(operation))
                && (targetTag == null || targetTag.isBlank())) {
            return "Target tag is required for " + operation + " operation";
        }

        return null;
    }

    @Override
    public void execute(Job job, Consumer<Job> progressCallback) {
        Map<String, Object> params = job.getParameters();
        String operation = (String) params.get("operation");
        String rootPath = (String) params.get("rootPath");
        String sourceTag = (String) params.get("sourceTag");
        String targetTag = (String) params.get("targetTag");
        boolean dryRun = Boolean.TRUE.equals(params.get("dryRun"));

        job.addLog(JobLogEntry.Level.INFO, "Starting bulk tag operation: " + operation);
        job.addLog(JobLogEntry.Level.INFO, "Root path: " + rootPath);
        job.addLog(JobLogEntry.Level.INFO, "Source tag: " + sourceTag);
        if (targetTag != null) {
            job.addLog(JobLogEntry.Level.INFO, "Target tag: " + targetTag);
        }
        if (dryRun) {
            job.addLog(JobLogEntry.Level.WARN, "DRY RUN MODE - No changes will be made");
        }

        List<SimulatedPage> pages = generateSimulatedPages(rootPath, sourceTag);
        job.setTotalItems(pages.size());
        progressCallback.accept(job);

        for (SimulatedPage page : pages) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                job.addLog(JobLogEntry.Level.WARN, "Job cancelled");
                break;
            }

            String message;
            JobResult.ResultStatus status;

            switch (operation) {
                case "find" -> {
                    status = JobResult.ResultStatus.SUCCESS;
                    message = "Found tag: " + sourceTag;
                }
                case "add" -> {
                    if (page.hasTag) {
                        status = JobResult.ResultStatus.SKIPPED;
                        message = "Page already has tag: " + targetTag;
                    } else {
                        status = JobResult.ResultStatus.SUCCESS;
                        message = dryRun
                                ? "[DRY RUN] Would add tag: " + targetTag
                                : "Added tag: " + targetTag;
                    }
                }
                case "remove" -> {
                    status = JobResult.ResultStatus.SUCCESS;
                    message = dryRun
                            ? "[DRY RUN] Would remove tag: " + sourceTag
                            : "Removed tag: " + sourceTag;
                }
                case "replace" -> {
                    status = JobResult.ResultStatus.SUCCESS;
                    message = dryRun
                            ? "[DRY RUN] Would replace " + sourceTag + " with " + targetTag
                            : "Replaced " + sourceTag + " with " + targetTag;
                }
                default -> {
                    status = JobResult.ResultStatus.ERROR;
                    message = "Unknown operation: " + operation;
                }
            }

            job.addResult(JobResult.builder()
                    .path(page.path)
                    .status(status)
                    .message(message)
                    .details(Map.of(
                            "currentTags", page.tags,
                            "operation", operation
                    ))
                    .build());

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            progressCallback.accept(job);
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Operation complete. Processed: %d, Success: %d, Skipped: %d, Errors: %d",
                job.getProcessedItems(), job.getSuccessCount(), job.getSkippedCount(), job.getErrorCount()));
    }

    private List<SimulatedPage> generateSimulatedPages(String rootPath, String sourceTag) {
        Random random = new Random(sourceTag.hashCode());
        List<SimulatedPage> pages = new ArrayList<>();

        String[] sections = {"en", "us", "products", "experiences"};

        for (String section : sections) {
            int pageCount = random.nextInt(5) + 2;
            for (int i = 0; i < pageCount; i++) {
                SimulatedPage page = new SimulatedPage();
                page.path = rootPath + "/" + section + "/page-" + i;
                page.hasTag = random.nextBoolean();
                page.tags = List.of(sourceTag, "common:category/featured");
                pages.add(page);
            }
        }

        return pages;
    }

    private static class SimulatedPage {
        String path;
        boolean hasTag;
        List<String> tags;
    }
}
