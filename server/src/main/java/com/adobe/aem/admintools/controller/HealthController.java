package com.adobe.aem.admintools.controller;

import com.adobe.aem.admintools.config.AemConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AemConfig aemConfig;

    @GetMapping("/")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "AEM Admin Tools",
                "version", "1.0.0"
        );
    }

    @GetMapping("/api/config")
    public Map<String, Object> getConfig() {
        return Map.of(
                "aemEnabled", aemConfig.isEnabled(),
                "aemAuthorUrl", aemConfig.getAuthorUrl() != null ? aemConfig.getAuthorUrl() : ""
        );
    }
}
