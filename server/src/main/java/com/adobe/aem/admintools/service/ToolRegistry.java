package com.adobe.aem.admintools.service;

import com.adobe.aem.admintools.model.ToolDefinition;
import com.adobe.aem.admintools.tool.AdminTool;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ToolRegistry {

    private final Map<String, AdminTool> tools = new HashMap<>();

    public ToolRegistry(List<AdminTool> adminTools) {
        for (AdminTool tool : adminTools) {
            register(tool);
        }
    }

    public void register(AdminTool tool) {
        tools.put(tool.getId(), tool);
    }

    public Optional<AdminTool> getTool(String id) {
        return Optional.ofNullable(tools.get(id));
    }

    public List<ToolDefinition> getAllTools() {
        return tools.values().stream()
                .map(AdminTool::getDefinition)
                .toList();
    }

    public List<ToolDefinition> getToolsByCategory(String category) {
        return tools.values().stream()
                .map(AdminTool::getDefinition)
                .filter(def -> def.getCategory().equals(category))
                .toList();
    }
}
