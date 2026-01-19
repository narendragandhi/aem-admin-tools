package com.adobe.aem.admintools.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobLogEntry {
    private Instant timestamp;
    private Level level;
    private String message;

    public enum Level {
        INFO, WARN, ERROR, DEBUG
    }
}
