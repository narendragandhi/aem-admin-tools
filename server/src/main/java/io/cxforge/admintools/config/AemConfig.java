package io.cxforge.admintools.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "aem")
@Data
public class AemConfig {
    private boolean enabled;
    private String authorUrl;
    private String publishUrl;
    private String username;
    private String password;

    private Timeout timeout = new Timeout();

    @Data
    public static class Timeout {
        private int connection = 10000;  // 10 seconds
        private int request = 30000;     // 30 seconds
        private int socket = 30000;      // 30 seconds
    }

    public String getBasicAuth() {
        return java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
    }
}
