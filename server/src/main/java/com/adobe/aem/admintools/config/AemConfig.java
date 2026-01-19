package com.adobe.aem.admintools.config;

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

    public String getBasicAuth() {
        return java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes());
    }
}
