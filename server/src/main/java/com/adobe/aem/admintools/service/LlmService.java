package com.adobe.aem.admintools.service;

import com.adobe.aem.admintools.config.LlmConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isEnabled() {
        return llmConfig.isEnabled();
    }

    public String getProvider() {
        return llmConfig.getProvider();
    }

    public String generate(String prompt) {
        return generate(prompt, null);
    }

    public String generate(String prompt, String systemPrompt) {
        if (!isEnabled()) {
            log.warn("LLM is disabled, returning empty response");
            return "";
        }

        try {
            return switch (llmConfig.getProvider().toLowerCase()) {
                case "ollama" -> generateWithOllama(prompt, systemPrompt);
                case "openai" -> generateWithOpenAI(prompt, systemPrompt);
                case "anthropic" -> generateWithAnthropic(prompt, systemPrompt);
                default -> {
                    log.warn("Unknown LLM provider: {}, falling back to Ollama", llmConfig.getProvider());
                    yield generateWithOllama(prompt, systemPrompt);
                }
            };
        } catch (Exception e) {
            log.error("LLM generation failed", e);
            return "";
        }
    }

    private String generateWithOllama(String prompt, String systemPrompt) throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost(llmConfig.getOllamaUrl() + "/api/generate");
            request.setHeader("Content-Type", "application/json");

            Map<String, Object> body = Map.of(
                    "model", llmConfig.getOllamaModel(),
                    "prompt", buildPrompt(systemPrompt, prompt),
                    "stream", false
            );

            request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = objectMapper.readTree(responseBody);
                    return json.has("response") ? json.get("response").asText() : "";
                } else {
                    log.error("Ollama request failed with status: {}", statusCode);
                    return "";
                }
            });
        }
    }

    private String generateWithOpenAI(String prompt, String systemPrompt) throws Exception {
        if (llmConfig.getOpenaiApiKey() == null || llmConfig.getOpenaiApiKey().isBlank()) {
            log.warn("OpenAI API key not configured");
            return "";
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.openai.com/v1/chat/completions");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + llmConfig.getOpenaiApiKey());

            List<Map<String, String>> messages = systemPrompt != null ?
                    List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", prompt)
                    ) :
                    List.of(Map.of("role", "user", "content", prompt));

            Map<String, Object> body = Map.of(
                    "model", llmConfig.getOpenaiModel(),
                    "messages", messages,
                    "max_tokens", 2048
            );

            request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = objectMapper.readTree(responseBody);
                    return json.path("choices").path(0).path("message").path("content").asText();
                } else {
                    String errorBody = EntityUtils.toString(response.getEntity());
                    log.error("OpenAI request failed with status: {} - {}", statusCode, errorBody);
                    return "";
                }
            });
        }
    }

    private String generateWithAnthropic(String prompt, String systemPrompt) throws Exception {
        if (llmConfig.getAnthropicApiKey() == null || llmConfig.getAnthropicApiKey().isBlank()) {
            log.warn("Anthropic API key not configured");
            return "";
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost request = new HttpPost("https://api.anthropic.com/v1/messages");
            request.setHeader("Content-Type", "application/json");
            request.setHeader("x-api-key", llmConfig.getAnthropicApiKey());
            request.setHeader("anthropic-version", "2023-06-01");

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("model", llmConfig.getAnthropicModel());
            body.put("max_tokens", 2048);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            if (systemPrompt != null) {
                body.put("system", systemPrompt);
            }

            request.setEntity(new StringEntity(objectMapper.writeValueAsString(body), ContentType.APPLICATION_JSON));

            return httpClient.execute(request, response -> {
                int statusCode = response.getCode();
                if (statusCode == 200) {
                    String responseBody = EntityUtils.toString(response.getEntity());
                    JsonNode json = objectMapper.readTree(responseBody);
                    JsonNode content = json.path("content");
                    if (content.isArray() && content.size() > 0) {
                        return content.get(0).path("text").asText();
                    }
                    return "";
                } else {
                    String errorBody = EntityUtils.toString(response.getEntity());
                    log.error("Anthropic request failed with status: {} - {}", statusCode, errorBody);
                    return "";
                }
            });
        }
    }

    private String buildPrompt(String systemPrompt, String userPrompt) {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt + "\n\n" + userPrompt;
        }
        return userPrompt;
    }

    public boolean testConnection() {
        try {
            String response = generate("Say 'OK' if you can read this.");
            return response != null && !response.isBlank();
        } catch (Exception e) {
            log.warn("LLM connection test failed: {}", e.getMessage());
            return false;
        }
    }
}
