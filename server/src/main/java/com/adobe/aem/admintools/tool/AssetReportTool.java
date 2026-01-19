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
public class AssetReportTool implements AdminTool {

    private final AemConfig aemConfig;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("asset-report")
                .name("Asset Report")
                .description("Generate reports on DAM assets including inventory, metadata gaps, and usage analysis")
                .category("Assets")
                .icon("document")
                .destructive(false)
                .requiresAem(false)
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
                                .name("includeSubfolders")
                                .label("Include Subfolders")
                                .description("Include assets in subfolders")
                                .type(ToolParameter.ParameterType.BOOLEAN)
                                .required(false)
                                .defaultValue(true)
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

        job.addLog(JobLogEntry.Level.INFO, "Generating " + reportType + " report for: " + damPath);

        List<SimulatedAsset> assets = generateSimulatedAssets(damPath);
        job.setTotalItems(assets.size());
        progressCallback.accept(job);

        Map<String, Integer> summary = new HashMap<>();

        for (SimulatedAsset asset : assets) {
            if (job.getStatus() == JobStatus.CANCELLED) {
                break;
            }

            boolean include = false;
            String message = "";
            Map<String, Object> details = new HashMap<>();

            switch (reportType) {
                case "inventory" -> {
                    include = true;
                    String type = asset.mimeType.split("/")[0];
                    summary.merge(type, 1, Integer::sum);
                    message = String.format("%s - %.2f MB", asset.mimeType, asset.sizeMB);
                    details.put("type", asset.mimeType);
                    details.put("size", asset.sizeMB + " MB");
                }
                case "missing-metadata" -> {
                    List<String> missing = new ArrayList<>();
                    if (asset.title == null) missing.add("title");
                    if (asset.description == null) missing.add("description");
                    if (asset.altText == null) missing.add("alt text");

                    if (!missing.isEmpty()) {
                        include = true;
                        message = "Missing: " + String.join(", ", missing);
                        details.put("missingFields", missing);
                    }
                }
                case "large-files" -> {
                    if (asset.sizeMB >= sizeThreshold) {
                        include = true;
                        message = String.format("%.2f MB (%s)", asset.sizeMB, asset.mimeType);
                        details.put("size", asset.sizeMB);
                        details.put("type", asset.mimeType);
                    }
                }
                case "unused-assets" -> {
                    if (asset.referenceCount == 0) {
                        include = true;
                        message = "No references found";
                        details.put("lastModified", asset.daysSinceModified + " days ago");
                    }
                }
            }

            if (include) {
                job.addResult(JobResult.builder()
                        .path(asset.path)
                        .status(JobResult.ResultStatus.SUCCESS)
                        .message(message)
                        .details(details)
                        .build());
            } else {
                job.addResult(JobResult.builder()
                        .path(asset.path)
                        .status(JobResult.ResultStatus.SKIPPED)
                        .message("Not included in " + reportType + " report")
                        .build());
            }

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            progressCallback.accept(job);
        }

        if ("inventory".equals(reportType)) {
            job.addLog(JobLogEntry.Level.INFO, "Asset breakdown by type:");
            summary.forEach((type, count) ->
                    job.addLog(JobLogEntry.Level.INFO, "  " + type + ": " + count));
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Report complete. Total assets: %d, Included in report: %d",
                job.getTotalItems(), job.getSuccessCount()));
    }

    private List<SimulatedAsset> generateSimulatedAssets(String damPath) {
        Random random = new Random(damPath.hashCode());
        List<SimulatedAsset> assets = new ArrayList<>();

        String[] folders = {"images", "documents", "videos"};
        String[][] mimeTypes = {
                {"image/jpeg", "image/png", "image/gif", "image/svg+xml"},
                {"application/pdf", "application/msword", "text/plain"},
                {"video/mp4", "video/quicktime"}
        };

        for (int f = 0; f < folders.length; f++) {
            int assetCount = random.nextInt(10) + 5;
            for (int i = 0; i < assetCount; i++) {
                SimulatedAsset asset = new SimulatedAsset();
                asset.path = damPath + "/" + folders[f] + "/asset-" + i + getExtension(mimeTypes[f][0]);
                asset.mimeType = mimeTypes[f][random.nextInt(mimeTypes[f].length)];
                asset.sizeMB = random.nextDouble() * 50;
                asset.title = random.nextDouble() > 0.3 ? "Asset " + i : null;
                asset.description = random.nextDouble() > 0.5 ? "Description for asset " + i : null;
                asset.altText = random.nextDouble() > 0.4 ? "Alt text " + i : null;
                asset.referenceCount = random.nextInt(5);
                asset.daysSinceModified = random.nextInt(365);

                assets.add(asset);
            }
        }

        return assets;
    }

    private String getExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "application/pdf" -> ".pdf";
            case "video/mp4" -> ".mp4";
            default -> "";
        };
    }

    private static class SimulatedAsset {
        String path;
        String mimeType;
        double sizeMB;
        String title;
        String description;
        String altText;
        int referenceCount;
        int daysSinceModified;
    }
}
