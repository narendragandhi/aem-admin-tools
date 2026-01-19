package com.adobe.aem.admintools.controller;

import com.adobe.aem.admintools.agui.AgentCard;
import com.adobe.aem.admintools.model.ToolDefinition;
import com.adobe.aem.admintools.model.ToolParameter;
import com.adobe.aem.admintools.service.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AgentCardController {

    private final ToolRegistry toolRegistry;

    @GetMapping("/.well-known/agent-card.json")
    public AgentCard getAgentCard() {
        List<AgentCard.AgentSkill> skills = toolRegistry.getAllTools().stream()
                .map(def -> AgentCard.AgentSkill.builder()
                        .id(def.getId())
                        .name(def.getName())
                        .description(def.getDescription())
                        .parameters(def.getParameters().stream()
                                .map(this::convertParameter)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        return AgentCard.builder()
                .name("AEM Admin Tools Agent")
                .description("AI-powered administrative tools for Adobe Experience Manager. " +
                        "Provides bulk content operations, asset management, and health checks.")
                .version("1.0.0")
                .protocolVersion("0.8")
                .url("http://localhost:10004")
                .capabilities(AgentCard.AgentCapabilities.builder()
                        .streaming(true)
                        .toolCalls(true)
                        .multiTurn(false)
                        .inputTypes(List.of("text", "structured"))
                        .outputTypes(List.of("text", "structured", "stream"))
                        .build())
                .skills(skills)
                .metadata(Map.of(
                        "author", "Adobe",
                        "category", "AEM Administration",
                        "aemIntegration", true
                ))
                .build();
    }

    @GetMapping("/")
    public Map<String, Object> getStatus() {
        return Map.of(
                "status", "running",
                "name", "AEM Admin Tools Agent",
                "version", "1.0.0",
                "protocolVersion", "0.8"
        );
    }

    private AgentCard.SkillParameter convertParameter(ToolParameter param) {
        return AgentCard.SkillParameter.builder()
                .name(param.getName())
                .type(param.getType().name().toLowerCase())
                .description(param.getDescription())
                .required(param.isRequired())
                .defaultValue(param.getDefaultValue())
                .options(param.getOptions())
                .build();
    }
}
