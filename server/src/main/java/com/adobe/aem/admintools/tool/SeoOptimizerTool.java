package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.*;
import com.adobe.aem.admintools.service.AemClientService;
import com.adobe.aem.admintools.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeoOptimizerTool implements AdminTool {

    private final AemClientService aemClient;
    private final LlmService llmService;

    private static final String SEO_SYSTEM_PROMPT = """
            You are an SEO expert specializing in web content optimization. Your task is to analyze page content and generate optimized SEO metadata.

            Guidelines:
            - Meta titles should be 50-60 characters, compelling, and include the primary keyword
            - Meta descriptions should be 150-160 characters, action-oriented, and include a call-to-action
            - Focus on user intent and search relevance
            - Use natural language, avoid keyword stuffing
            - Consider the page's purpose and target audience

            Respond in JSON format only, with no additional text:
            {
                "title": "optimized meta title",
                "description": "optimized meta description",
                "keywords": ["keyword1", "keyword2", "keyword3"],
                "suggestions": ["suggestion 1", "suggestion 2"]
            }
            """;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("seo-optimizer")
                .name("AI SEO Optimizer")
                .description("Uses AI to analyze pages and generate optimized SEO titles, descriptions, and keywords")
                .category("Content")
                .icon("sparkle")
                .destructive(false)
                .requiresAem(true)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("rootPath")
                                .label("Root Path")
                                .description("The content path to scan for SEO optimization")
                                .type(ToolParameter.ParameterType.PATH)
                                .required(true)
                                .defaultValue("/content/we-retail")
                                .build(),
                        ToolParameter.builder()
                                .name("mode")
                                .label("Mode")
                                .description("Analysis mode")
                                .type(ToolParameter.ParameterType.SELECT)
                                .required(true)
                                .options(List.of("analyze", "generate-suggestions"))
                                .defaultValue("analyze")
                                .build(),
                        ToolParameter.builder()
                                .name("focusKeyword")
                                .label("Focus Keyword")
                                .description("Optional primary keyword to optimize for")
                                .type(ToolParameter.ParameterType.STRING)
                                .required(false)
                                .build(),
                        ToolParameter.builder()
                                .name("maxPages")
                                .label("Max Pages to Analyze")
                                .description("Maximum number of pages to analyze")
                                .type(ToolParameter.ParameterType.NUMBER)
                                .required(false)
                                .defaultValue(20)
                                .build()
                ))
                .build();
    }

    @Override
    public String validateParameters(Map<String, Object> parameters) {
        String rootPath = (String) parameters.get("rootPath");
        if (rootPath == null || !rootPath.startsWith("/content")) {
            return "Root path must start with /content";
        }
        return null;
    }

    @Override
    public void execute(Job job, Consumer<Job> progressCallback) {
        Map<String, Object> params = job.getParameters();
        String rootPath = (String) params.get("rootPath");
        String mode = (String) params.get("mode");
        String focusKeyword = (String) params.get("focusKeyword");
        int maxPages = ((Number) params.getOrDefault("maxPages", 20)).intValue();

        job.addLog(JobLogEntry.Level.INFO, "Starting AI SEO analysis on: " + rootPath);
        job.addLog(JobLogEntry.Level.INFO, "Mode: " + mode);
        job.addLog(JobLogEntry.Level.INFO, "LLM Provider: " + llmService.getProvider());

        if (!aemClient.isEnabled()) {
            job.addLog(JobLogEntry.Level.ERROR, "AEM connection is not enabled. Please configure AEM credentials.");
            return;
        }

        if (!llmService.isEnabled()) {
            job.addLog(JobLogEntry.Level.WARN, "LLM is disabled. Running in analysis-only mode.");
        }

        try {
            job.addLog(JobLogEntry.Level.INFO, "Querying AEM for pages...");
            List<Map<String, Object>> pages = aemClient.findPages(rootPath, maxPages);

            if (pages.isEmpty()) {
                job.addLog(JobLogEntry.Level.WARN, "No pages found under: " + rootPath);
                return;
            }

            job.addLog(JobLogEntry.Level.INFO, "Found " + pages.size() + " pages to analyze");
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
                    processPage(job, pagePath, page, mode, focusKeyword);
                } catch (Exception e) {
                    job.addResult(JobResult.builder()
                            .path(pagePath)
                            .status(JobResult.ResultStatus.ERROR)
                            .message("Error analyzing page: " + e.getMessage())
                            .build());
                }

                progressCallback.accept(job);
            }

        } catch (Exception e) {
            log.error("Failed to process SEO analysis", e);
            job.addLog(JobLogEntry.Level.ERROR, "Failed to process: " + e.getMessage());
        }

        job.addLog(JobLogEntry.Level.INFO, String.format(
                "Analysis complete. Processed: %d, Optimized: %d, Needs Improvement: %d",
                job.getProcessedItems(), job.getSuccessCount(), job.getErrorCount()));
    }

    @SuppressWarnings("unchecked")
    private void processPage(Job job, String pagePath, Map<String, Object> pageData,
                            String mode, String focusKeyword) throws Exception {

        Map<String, Object> jcrContent = (Map<String, Object>) pageData.get("jcr:content");
        if (jcrContent == null) {
            jcrContent = aemClient.getPageProperties(pagePath);
        }

        if (jcrContent == null) {
            job.addResult(JobResult.builder()
                    .path(pagePath)
                    .status(JobResult.ResultStatus.ERROR)
                    .message("Missing jcr:content node")
                    .build());
            return;
        }

        String currentTitle = (String) jcrContent.get("jcr:title");
        String currentDescription = (String) jcrContent.get("jcr:description");
        String pageTitle = (String) jcrContent.get("pageTitle");

        Map<String, Object> details = new HashMap<>();
        details.put("currentTitle", currentTitle != null ? currentTitle : "");
        details.put("currentDescription", currentDescription != null ? currentDescription : "");

        List<String> issues = new ArrayList<>();

        // Analyze current SEO status
        if (currentTitle == null || currentTitle.isBlank()) {
            issues.add("Missing page title");
        } else if (currentTitle.length() < 30) {
            issues.add("Title too short (< 30 chars)");
        } else if (currentTitle.length() > 70) {
            issues.add("Title too long (> 70 chars)");
        }

        if (currentDescription == null || currentDescription.isBlank()) {
            issues.add("Missing meta description");
        } else if (currentDescription.length() < 120) {
            issues.add("Description too short (< 120 chars)");
        } else if (currentDescription.length() > 160) {
            issues.add("Description too long (> 160 chars)");
        }

        // Calculate SEO score
        int seoScore = 100;
        seoScore -= issues.size() * 20;
        seoScore = Math.max(0, seoScore);
        details.put("seoScore", seoScore);
        details.put("issues", issues);

        // Generate AI suggestions if enabled and requested
        if ("generate-suggestions".equals(mode) && llmService.isEnabled()) {
            String prompt = buildSeoPrompt(pagePath, currentTitle, currentDescription, focusKeyword);
            String aiResponse = llmService.generate(prompt, SEO_SYSTEM_PROMPT);

            if (aiResponse != null && !aiResponse.isBlank()) {
                try {
                    // Parse JSON response from LLM
                    Map<String, Object> suggestions = parseAiResponse(aiResponse);
                    details.put("aiSuggestions", suggestions);
                    details.put("suggestedTitle", suggestions.get("title"));
                    details.put("suggestedDescription", suggestions.get("description"));
                    details.put("suggestedKeywords", suggestions.get("keywords"));
                } catch (Exception e) {
                    log.warn("Failed to parse AI response for {}: {}", pagePath, e.getMessage());
                    details.put("aiError", "Failed to parse AI suggestions");
                }
            }
        }

        JobResult.ResultStatus status;
        String message;

        if (issues.isEmpty()) {
            status = JobResult.ResultStatus.SUCCESS;
            message = "SEO looks good! Score: " + seoScore + "/100";
        } else {
            status = JobResult.ResultStatus.ERROR;
            message = "Needs improvement: " + String.join(", ", issues) + ". Score: " + seoScore + "/100";
        }

        job.addResult(JobResult.builder()
                .path(pagePath)
                .status(status)
                .message(message)
                .details(details)
                .build());
    }

    private String buildSeoPrompt(String pagePath, String currentTitle, String currentDescription, String focusKeyword) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze and optimize SEO for this page:\n\n");
        prompt.append("Page Path: ").append(pagePath).append("\n");
        prompt.append("Current Title: ").append(currentTitle != null ? currentTitle : "(none)").append("\n");
        prompt.append("Current Description: ").append(currentDescription != null ? currentDescription : "(none)").append("\n");

        if (focusKeyword != null && !focusKeyword.isBlank()) {
            prompt.append("Focus Keyword: ").append(focusKeyword).append("\n");
        }

        prompt.append("\nGenerate optimized SEO metadata in JSON format.");
        return prompt.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseAiResponse(String response) throws Exception {
        // Extract JSON from response (handle cases where LLM adds extra text)
        String json = response;
        int jsonStart = response.indexOf("{");
        int jsonEnd = response.lastIndexOf("}");

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            json = response.substring(jsonStart, jsonEnd + 1);
        }

        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return mapper.readValue(json, Map.class);
    }
}
