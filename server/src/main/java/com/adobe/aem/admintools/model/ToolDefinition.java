package com.adobe.aem.admintools.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ToolDefinition {
    private String id;
    private String name;
    private String description;
    private String category;
    private String icon;
    private List<ToolParameter> parameters;
    private boolean destructive;
    private boolean requiresAem;
}
