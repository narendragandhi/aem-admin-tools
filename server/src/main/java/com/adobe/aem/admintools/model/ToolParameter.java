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
public class ToolParameter {
    private String name;
    private String label;
    private String description;
    private ParameterType type;
    private boolean required;
    private Object defaultValue;
    private List<String> options; // For SELECT type

    public enum ParameterType {
        STRING,
        PATH,
        NUMBER,
        BOOLEAN,
        SELECT,
        MULTISELECT
    }
}
