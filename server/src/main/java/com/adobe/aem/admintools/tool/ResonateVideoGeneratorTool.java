package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.Job;
import com.adobe.aem.admintools.model.JobLogEntry;
import com.adobe.aem.admintools.model.JobResult;
import com.adobe.aem.admintools.model.ToolDefinition;
import com.adobe.aem.admintools.model.ToolParameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResonateVideoGeneratorTool implements AdminTool {

    // In a real scenario, this would be an injected service for video generation
    // private final VideoGenerationService videoGenerationService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .id("resonate-video-generator")
                .name("Resonate Video Generator")
                .description("Generates a video based on a text prompt.")
                .category("Content")
                .icon("movie") // Assuming a suitable icon exists
                .destructive(false)
                .requiresAem(false) // This tool might not directly require AEM connection for video generation
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("videoPrompt")
                                .label("Video Prompt")
                                .description("Text prompt to generate the video.")
                                .type(ToolParameter.ParameterType.STRING)
                                .required(true)
                                .build()
                ))
                .build();
    }

    @Override
    public String validateParameters(Map<String, Object> parameters) {
        String videoPrompt = (String) parameters.get("videoPrompt");
        if (videoPrompt == null || videoPrompt.trim().isEmpty()) {
            return "Video prompt cannot be empty.";
        }
        return null;
    }

    @Override
    public void execute(Job job, Consumer<Job> progressCallback) {
        Map<String, Object> params = job.getParameters();
        String videoPrompt = (String) params.get("videoPrompt");

        job.addLog(JobLogEntry.Level.INFO, "Starting video generation for prompt: '" + videoPrompt + "'");

        try {
            // Simulate video generation (replace with actual service call)
            Thread.sleep(5000); // Simulate a 5-second video generation process

            String generatedVideoUrl = "https://example.com/generated-video-" + System.currentTimeMillis() + ".mp4";

            job.addResult(JobResult.builder()
                    .path(generatedVideoUrl)
                    .status(JobResult.ResultStatus.SUCCESS)
                    .message("Video generated successfully!")
                    .details(Map.of("url", generatedVideoUrl, "prompt", videoPrompt))
                    .build());

            job.addLog(JobLogEntry.Level.INFO, "Video generated: " + generatedVideoUrl);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            job.addLog(JobLogEntry.Level.ERROR, "Video generation interrupted: " + e.getMessage());
            job.addResult(JobResult.builder()
                    .status(JobResult.ResultStatus.ERROR)
                    .message("Video generation interrupted.")
                    .build());
        } catch (Exception e) {
            job.addLog(JobLogEntry.Level.ERROR, "Failed to generate video: " + e.getMessage());
            job.addResult(JobResult.builder()
                    .status(JobResult.ResultStatus.ERROR)
                    .message("Failed to generate video.")
                    .build());
        } finally {
            progressCallback.accept(job);
        }
    }
}
