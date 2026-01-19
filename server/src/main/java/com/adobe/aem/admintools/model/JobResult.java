package com.adobe.aem.admintools.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobResult {
    private String path;
    private ResultStatus status;
    private String message;
    private Map<String, Object> details;

    public enum ResultStatus {
        SUCCESS, ERROR, SKIPPED
    }
}
