package com.adobe.aem.admintools.agui;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentCard {
    private String name;
    private String description;
    private String version;
    private String protocolVersion;
    private String url;
    private AgentCapabilities capabilities;
    private List<AgentSkill> skills;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentCapabilities {
        private boolean streaming;
        private boolean toolCalls;
        private boolean multiTurn;
        private List<String> inputTypes;
        private List<String> outputTypes;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AgentSkill {
        private String id;
        private String name;
        private String description;
        private List<SkillParameter> parameters;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillParameter {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
        private List<String> options;
    }
}
