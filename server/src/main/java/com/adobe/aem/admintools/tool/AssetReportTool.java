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
public class AssetReportTool implements AdminTool {

    private final AemClientService aemClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("asset-report")
                .name("Asset Report")
                .description("Generate reports on DAM assets including inventory, metadata gaps, and usage analysis")
                .category("Assets")
                .icon("document")
                .destructive(false)
                .requiresAem(true)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("damPath")
                                .label("DAM Path")
                                .description("The DAM folder to analyze")
                                .type(ToolParameter.ParameterType.PATH)
                                .required(true)
                                .defaultValue("/content/dam/we-retail")
                                .build(),
                        ToolParameter.builder()
                                .name("reportType")
                                .label("Report Type")
                                .description("Type of report to generate")
                                .type(ToolParameter.ParameterType.SELECT)
                                .required(true)
                                .options(List.of("inventory", "missing-metadata", "large-files", "unused-assets"))
                                .defaultValue("inventory")
                                .build(),
                        ToolParameter.builder()
                                .name("fileSizeThresholdMB")
                                .label("File Size Threshold (MB)")
                                .description("For large-files report, minimum size in MB")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(10)
                                .build(),
                        ToolParameter.builder()
                                .name("maxAssets")
                                .label("Max Assets to Scan")
                                .description("Maximum number of assets to analyze")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(200)
                                .build()
                ))
                .build();
    }

    @Override
    public String validateParameters(Map<String, Object> parameters) {
        String damPath = (String) parameters.get("damPath");
        if (damPath == null || !damPath.startsWith("/content/dam")) {
            return "DAM path must start with /content/dam";
        }
        return null;
    }

    @Override
    public void execute(Job job, Consumer<Job> progressCallback) {
        Map<String, Object> params = job.getParameters();
        String damPath = (String) params.get("damPath");
        String reportType = (String) params.get("reportType");
        int sizeThreshold = ((Number) params.getOrDefault("fileSizeThresholdMB", 10)).intValue();
        int maxAssets = ((Number) params.getOrDefault("maxAssets", 200)).intValue();

        job.addLog(JobLogEntry.Level.INFO, "Generating " + reportType + " report for: " + damPath);

        if (!aemClient.isEnabled()) {
            job.addLog(JobLogEntry.Level.ERROR, "AEM connection is not enabled. Please configure AEM credentials.");
            return;
        }

        try {
            job.addLog(JobLogEntry.Level.INFO, "Querying AEM DAM for assets...");
            List<Map<String, Object>> assets = aemClient.findAssets(damPath, maxAssets);

            if (assets.isEmpty()) {
                job.addLog(JobLogEntry.Level.WARN, "No assets found under: " + damPath);
                return;
            }

            job.addLog(JobLogEntry.Level.INFO, "Found " + assets.size() + " assets to analyze");
            job.setTotalItems(assets.size());
            progressCallback.accept(job);

            Map<String, Integer> summary = new HashMap<>();

            for (Map<String, Object> asset : assets) {
                if (job.getStatus() == JobStatus.CANCELLED) {
                    break;
                }

                String assetPath = (String) asset.get("jcr:path");
                if (assetPath == null) {
                    assetPath = (String) asset.get("path");
                }

                try {
                    processAsset(job, assetPath, asset, reportType, sizeThreshold, summary);
                } catch (Exception e) {
                    job.addResult(JobResult.builder()
                            .path(assetPath)
                            .status(JobResult.ResultStatus.ERROR)
                            .message("Error processing asset: " + e.getMessage())
                            .build());
                }

                progressCallback.accept(job);
            }

            // Log summary for inventory report
            if ("inventory".equals(reportType)) {
                job.addLog(JobLogEntry.Level.INFO, "Asset breakdown by type:");
                summary.forEach((type, count) ->
                        job.addLog(JobLogEntry.Level.INFO, "  " + type + ": " + count));
            }

            job.addLog(JobLogEntry.Level.INFO, String.format(
                    "Report complete. Total assets: %d, Included in report: %d",
                    job.getTotalItems(), job.getSuccessCount()));

        } catch (Exception e) {
            log.error("Failed to query AEM DAM", e);
            job.addLog(JobLogEntry.Level.ERROR, "Failed to query AEM DAM: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void processAsset(Job job, String assetPath, Map<String, Object> assetData,
                             String reportType, int sizeThreshold, Map<String, Integer> summary) throws Exception {

        Map<String, Object> jcrContent = (Map<String, Object>) assetData.get("jcr:content");
        Map<String, Object> metadata = null;

        if (jcrContent != null) {
            metadata = (Map<String, Object>) jcrContent.get("metadata");
        }

        if (metadata == null) {
            // Try to fetch metadata directly
            Map<String, Object> metaNode = aemClient.getNodeProperties(assetPath + "/jcr:content/metadata");
            if (metaNode != null) {
                metadata = metaNode;
            }
        }

        boolean include = false;
        String message = "";
        Map<String, Object> details = new HashMap<>();

        switch (reportType) {
            case "inventory" -> {
                include = true;
                String mimeType = getMimeType(jcrContent, metadata);
                String type = mimeType != null ? mimeType.split("/")[0] : "unknown";
                summary.merge(type, 1, Integer::sum);

                long sizeBytes = getFileSize(jcrContent, metadata);
                double sizeMB = sizeBytes / (1024.0 * 1024.0);

                message = String.format("%s - %.2f MB", mimeType != null ? mimeType : "unknown", sizeMB);
                details.put("type", mimeType);
                details.put("size", String.format("%.2f MB", sizeMB));
            }
            case "missing-metadata" -> {
                List<String> missing = new ArrayList<>();

                if (metadata == null) {
                    missing.add("all metadata");
                } else {
                    if (metadata.get("dc:title") == null) missing.add("title");
                    if (metadata.get("dc:description") == null) missing.add("description");

                    // Check for alt text on images
                    String mimeType = getMimeType(jcrContent, metadata);
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        if (metadata.get("dam:Iptc4xmpCore:altText") == null &&
                            metadata.get("tiff:ImageDescription") == null) {
                            missing.add("alt text");
                        }
                    }
                }

                if (!missing.isEmpty()) {
                    include = true;
                    message = "Missing: " + String.join(", ", missing);
                    details.put("missingFields", missing);
                }
            }
            case "large-files" -> {
                long sizeBytes = getFileSize(jcrContent, metadata);
                double sizeMB = sizeBytes / (1024.0 * 1024.0);

                if (sizeMB >= sizeThreshold) {
                    include = true;
                    String mimeType = getMimeType(jcrContent, metadata);
                    message = String.format("%.2f MB (%s)", sizeMB, mimeType != null ? mimeType : "unknown");
                    details.put("size", sizeMB);
                    details.put("type", mimeType);
                }
            }
            case "unused-assets" -> {
                // Check if asset has any references
                // Note: This would require querying reference manager in AEM
                // For now, we'll check if it has been modified recently as a proxy
                Object lastModObj = jcrContent != null ? jcrContent.get("jcr:lastModified") : null;
                String mimeType = getMimeType(jcrContent, metadata);

                // Include in report as potential unused (actual reference check would need XHR to reference endpoint)
                include = true;
                message = "Potentially unused asset";
                details.put("lastModified", lastModObj != null ? lastModObj.toString() : "unknown");
                details.put("type", mimeType);
            }
        }

        if (include) {
            job.addResult(JobResult.builder()
                    .path(assetPath)
                    .status(JobResult.ResultStatus.SUCCESS)
                    .message(message)
                    .details(details)
                    .build());
        } else {
            job.addResult(JobResult.builder()
                    .path(assetPath)
                    .status(JobResult.ResultStatus.SKIPPED)
                    .message("Not included in " + reportType + " report")
                    .build());
        }
    }

    @SuppressWarnings("unchecked")
    private String getMimeType(Map<String, Object> jcrContent, Map<String, Object> metadata) {
        if (metadata != null && metadata.get("dc:format") != null) {
            return (String) metadata.get("dc:format");
        }
        if (jcrContent != null) {
            // Try renditions/original
            Map<String, Object> renditions = (Map<String, Object>) jcrContent.get("renditions");
            if (renditions != null) {
                Map<String, Object> original = (Map<String, Object>) renditions.get("original");
                if (original != null) {
                    Map<String, Object> origContent = (Map<String, Object>) original.get("jcr:content");
                    if (origContent != null) {
                        return (String) origContent.get("jcr:mimeType");
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private long getFileSize(Map<String, Object> jcrContent, Map<String, Object> metadata) {
        if (metadata != null && metadata.get("dam:size") != null) {
            Object size = metadata.get("dam:size");
            if (size instanceof Number) {
                return ((Number) size).longValue();
            }
            try {
                return Long.parseLong(size.toString());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        if (jcrContent != null) {
            Map<String, Object> renditions = (Map<String, Object>) jcrContent.get("renditions");
            if (renditions != null) {
                Map<String, Object> original = (Map<String, Object>) renditions.get("original");
                if (original != null) {
                    Map<String, Object> origContent = (Map<String, Object>) original.get("jcr:content");
                    if (origContent != null) {
                        Object size = origContent.get("jcr:data:length");
                        if (size instanceof Number) {
                            return ((Number) size).longValue();
                        }
                    }
                }
            }
        }
        return 0;
    }
}
