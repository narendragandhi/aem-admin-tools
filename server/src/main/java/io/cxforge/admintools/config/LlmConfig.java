package io.cxforge.admintools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Data
public class LlmConfig {
    private boolean enabled = true;
    private String provider = "ollama"; // ollama, openai, anthropic

    // Ollama settings (default)
    private String ollamaUrl = "http://localhost:11434";
    private String ollamaModel = "llama3.2";

    // OpenAI settings
    private String openaiApiKey;
    private String openaiModel = "gpt-4o-mini";

    // Anthropic settings
    private String anthropicApiKey;
    private String anthropicModel = "claude-3-haiku-20240307";

    // Timeout settings
    private Timeout timeout = new Timeout();

    @Data
    public static class Timeout {
        private int connection = 10000;  // 10 seconds
        private int request = 60000;     // 60 seconds (LLM calls can be slow)
        private int socket = 60000;      // 60 seconds
    }
}
