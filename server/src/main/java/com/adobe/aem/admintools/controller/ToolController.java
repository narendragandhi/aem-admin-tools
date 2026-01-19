package com.adobe.aem.admintools.controller;

import com.adobe.aem.admintools.model.ToolDefinition;
import com.adobe.aem.admintools.service.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolRegistry toolRegistry;

    @GetMapping
    public List<ToolDefinition> getAllTools() {
        return toolRegistry.getAllTools();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ToolDefinition> getTool(@PathVariable String id) {
        return toolRegistry.getTool(id)
                .map(tool -> ResponseEntity.ok(tool.getDefinition()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public Map<String, List<ToolDefinition>> getToolsByCategory() {
        return toolRegistry.getAllTools().stream()
                .collect(java.util.stream.Collectors.groupingBy(ToolDefinition::getCategory));
    }
}
