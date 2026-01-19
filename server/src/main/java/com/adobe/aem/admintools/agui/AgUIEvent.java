package com.adobe.aem.admintools.agui;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgUIEvent {
    private String type;
    private long timestamp;
    private Map<String, Object> data;

    public static AgUIEvent runStarted(String runId) {
        return AgUIEvent.builder()
                .type("RUN_STARTED")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId))
                .build();
    }

    public static AgUIEvent runFinished(String runId, Map<String, Object> result) {
        return AgUIEvent.builder()
                .type("RUN_FINISHED")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "result", result))
                .build();
    }

    public static AgUIEvent runError(String runId, String error) {
        return AgUIEvent.builder()
                .type("RUN_ERROR")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "error", error))
                .build();
    }

    public static AgUIEvent textMessageStart(String runId, String messageId, String field) {
        return AgUIEvent.builder()
                .type("TEXT_MESSAGE_START")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "messageId", messageId, "field", field))
                .build();
    }

    public static AgUIEvent textMessageDelta(String runId, String messageId, String field, String delta, String content) {
        return AgUIEvent.builder()
                .type("TEXT_MESSAGE_DELTA")
                .timestamp(System.currentTimeMillis())
                .data(Map.of(
                        "runId", runId,
                        "messageId", messageId,
                        "field", field,
                        "delta", delta,
                        "content", content
                ))
                .build();
    }

    public static AgUIEvent textMessageEnd(String runId, String messageId) {
        return AgUIEvent.builder()
                .type("TEXT_MESSAGE_END")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "messageId", messageId))
                .build();
    }

    public static AgUIEvent stateDelta(String runId, Map<String, Object> state) {
        return AgUIEvent.builder()
                .type("STATE_DELTA")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "state", state))
                .build();
    }

    public static AgUIEvent toolCallStart(String runId, String toolId, String toolName) {
        return AgUIEvent.builder()
                .type("TOOL_CALL_START")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "toolId", toolId, "toolName", toolName))
                .build();
    }

    public static AgUIEvent toolCallEnd(String runId, String toolId, Map<String, Object> result) {
        return AgUIEvent.builder()
                .type("TOOL_CALL_END")
                .timestamp(System.currentTimeMillis())
                .data(Map.of("runId", runId, "toolId", toolId, "result", result))
                .build();
    }
}
