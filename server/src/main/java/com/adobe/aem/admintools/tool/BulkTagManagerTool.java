package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.*;
import com.adobe.aem.admintools.service.AemClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class BulkTagManagerTool implements AdminTool {

    private final AemClientService aemClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("bulk-tag-manager")
                .name("Bulk Tag Manager")
                .description("Add, remove, or replace tags across multiple content pages")
                .category("Content")
                .icon("tag")
                .destructive(true)
                .requiresAem(true)
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
                                .description("The tag to find/remove/replace (e.g., we-retail:activity/hiking)")
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
                                .name("maxPages")
                                .label("Max Pages to Process")
                                .description("Maximum number of pages to process")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(100)
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
        int maxPages = ((Number) params.getOrDefault("maxPages", 100)).intValue();
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

        if (!aemClient.isEnabled()) {
            job.addLog(JobLogEntry.Level.ERROR, "AEM connection is not enabled. Please configure AEM credentials.");
            return;
        }

        try {
            List<Map<String, Object>> pages;

            if ("find".equals(operation) || "remove".equals(operation) || "replace".equals(operation)) {
                // For these operations, find pages that have the source tag
                job.addLog(JobLogEntry.Level.INFO, "Searching for pages with tag: " + sourceTag);
                pages = aemClient.findPagesWithTag(rootPath, sourceTag, maxPages);
            } else {
                // For add operation, find all pages under the path
                job.addLog(JobLogEntry.Level.INFO, "Finding all pages under: " + rootPath);
                pages = aemClient.findPages(rootPath, maxPages);
            }

            if (pages.isEmpty()) {
                job.addLog(JobLogEntry.Level.WARN, "No pages found matching criteria");
                return;
            }

            job.addLog(JobLogEntry.Level.INFO, "Found " + pages.size() + " pages to process");
            job.setTotalItems(pages.size());
            progressCallback.accept(job);

            for (Map<String, Object> page : pages) {
                if (job.getStatus() == JobStatus.CANCELLED) {
                    job.addLog(JobLogEntry.Level.WARN, "Job cancelled");
                    break;
                }

                String pagePath = (String) page.get("jcr:path");
                if (pagePath == null) {
                    pagePath = (String) page.get("path");
                }

                try {
                    processPage(job, pagePath, operation, sourceTag, targetTag, dryRun);
                } catch (Exception e) {
                    job.addResult(JobResult.builder()
                            .path(pagePath)
                            .status(JobResult.ResultStatus.ERROR)
                            .message("Error processing page: " + e.getMessage())
                            .build());
                }

                progressCallback.accept(job);
            }

        } catch (Exception e) {
            log.error("Failed to process bulk tag operation", e);
            job.addLog(JobLogEntry.Level.ERROR, "Failed to process: " + e.getMessage());
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Operation complete. Processed: %d, Success: %d, Skipped: %d, Errors: %d",
                job.getProcessedItems(), job.getSuccessCount(), job.getSkippedCount(), job.getErrorCount()));
    }

    @SuppressWarnings("unchecked")
    private void processPage(Job job, String pagePath, String operation,
                            String sourceTag, String targetTag, boolean dryRun) throws Exception {

        Map<String, Object> pageProps = aemClient.getPageProperties(pagePath);
        List<String> currentTags = new ArrayList<>();

        if (pageProps != null) {
            Object tagsObj = pageProps.get("cq:tags");
            if (tagsObj instanceof List) {
                currentTags.addAll((List<String>) tagsObj);
            } else if (tagsObj instanceof String) {
                currentTags.add((String) tagsObj);
            }
        }

        String message;
        JobResult.ResultStatus status;

        switch (operation) {
            case "find" -> {
                if (currentTags.contains(sourceTag)) {
                    status = JobResult.ResultStatus.SUCCESS;
                    message = "Found tag: " + sourceTag;
                } else {
                    status = JobResult.ResultStatus.SKIPPED;
                    message = "Tag not found on page";
                }
            }
            case "add" -> {
                if (currentTags.contains(targetTag)) {
                    status = JobResult.ResultStatus.SKIPPED;
                    message = "Page already has tag: " + targetTag;
                } else {
                    if (dryRun) {
                        status = JobResult.ResultStatus.SUCCESS;
                        message = "[DRY RUN] Would add tag: " + targetTag;
                    } else {
                        String result = aemClient.addTag(pagePath, targetTag);
                        if ("Success".equals(result)) {
                            status = JobResult.ResultStatus.SUCCESS;
                            message = "Added tag: " + targetTag;
                        } else {
                            status = JobResult.ResultStatus.ERROR;
                            message = "Failed to add tag: " + result;
                        }
                    }
                }
            }
            case "remove" -> {
                if (!currentTags.contains(sourceTag)) {
                    status = JobResult.ResultStatus.SKIPPED;
                    message = "Tag not found on page: " + sourceTag;
                } else {
                    if (dryRun) {
                        status = JobResult.ResultStatus.SUCCESS;
                        message = "[DRY RUN] Would remove tag: " + sourceTag;
                    } else {
                        String result = aemClient.removeTag(pagePath, sourceTag);
                        if ("Success".equals(result)) {
                            status = JobResult.ResultStatus.SUCCESS;
                            message = "Removed tag: " + sourceTag;
                        } else {
                            status = JobResult.ResultStatus.ERROR;
                            message = "Failed to remove tag: " + result;
                        }
                    }
                }
            }
            case "replace" -> {
                if (!currentTags.contains(sourceTag)) {
                    status = JobResult.ResultStatus.SKIPPED;
                    message = "Source tag not found on page: " + sourceTag;
                } else {
                    if (dryRun) {
                        status = JobResult.ResultStatus.SUCCESS;
                        message = "[DRY RUN] Would replace " + sourceTag + " with " + targetTag;
                    } else {
                        String result = aemClient.replaceTag(pagePath, sourceTag, targetTag);
                        if ("Success".equals(result)) {
                            status = JobResult.ResultStatus.SUCCESS;
                            message = "Replaced " + sourceTag + " with " + targetTag;
                        } else {
                            status = JobResult.ResultStatus.ERROR;
                            message = "Failed to replace tag: " + result;
                        }
                    }
                }
            }
            default -> {
                status = JobResult.ResultStatus.ERROR;
                message = "Unknown operation: " + operation;
            }
        }

        job.addResult(JobResult.builder()
                .path(pagePath)
                .status(status)
                .message(message)
                .details(Map.of(
                        "currentTags", currentTags,
                        "operation", operation,
                        "dryRun", dryRun
                ))
                .build());
    }
}
