package io.cxforge.admintools.controller;

import io.cxforge.admintools.model.ToolDefinition;
import io.cxforge.admintools.service.ToolRegistry;
import io.cxforge.admintools.tool.AdminTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for ToolController.
 * Uses standalone MockMvc setup to avoid loading Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ToolControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ToolRegistry toolRegistry;

    @InjectMocks
    private ToolController toolController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(toolController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();
    }

    // Exception handler for tests
    @RestControllerAdvice
    static class TestExceptionHandler {
        @ExceptionHandler(io.cxforge.admintools.exception.ToolNotFoundException.class)
        public ResponseEntity<Map<String, String>> handleToolNotFound(io.cxforge.admintools.exception.ToolNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Test
    @DisplayName("GET /api/v1/tools returns list of tools")
    void testGetAllTools() throws Exception {
        ToolDefinition tool1 = ToolDefinition.builder()
                .id("content-health-check")
                .name("Content Health Check")
                .description("Scans content for issues")
                .category("Content")
                .build();

        ToolDefinition tool2 = ToolDefinition.builder()
                .id("asset-report")
                .name("Asset Report")
                .description("Generates asset reports")
                .category("Assets")
                .build();

        when(toolRegistry.getAllTools()).thenReturn(List.of(tool1, tool2));

        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is("content-health-check")))
                .andExpect(jsonPath("$[0].name", is("Content Health Check")))
                .andExpect(jsonPath("$[1].id", is("asset-report")));
    }

    @Test
    @DisplayName("GET /api/v1/tools returns empty list when no tools registered")
    void testGetAllTools_empty() throws Exception {
        when(toolRegistry.getAllTools()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/v1/tools/{id} returns tool definition")
    void testGetToolById() throws Exception {
        ToolDefinition toolDef = ToolDefinition.builder()
                .id("content-health-check")
                .name("Content Health Check")
                .description("Scans content for issues")
                .category("Content")
                .icon("health")
                .destructive(false)
                .requiresAem(true)
                .build();

        AdminTool mockTool = mock(AdminTool.class);
        when(mockTool.getDefinition()).thenReturn(toolDef);
        when(toolRegistry.getTool("content-health-check"))
                .thenReturn(Optional.of(mockTool));

        mockMvc.perform(get("/api/v1/tools/content-health-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("content-health-check")))
                .andExpect(jsonPath("$.name", is("Content Health Check")))
                .andExpect(jsonPath("$.category", is("Content")))
                .andExpect(jsonPath("$.icon", is("health")))
                .andExpect(jsonPath("$.destructive", is(false)))
                .andExpect(jsonPath("$.requiresAem", is(true)));
    }

    @Test
    @DisplayName("GET /api/v1/tools/{id} returns 404 for unknown tool")
    void testGetToolById_notFound() throws Exception {
        when(toolRegistry.getTool("unknown-tool"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tools/unknown-tool"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/tools returns tools with parameters")
    void testGetAllTools_withParameters() throws Exception {
        ToolDefinition tool = ToolDefinition.builder()
                .id("bulk-tag-manager")
                .name("Bulk Tag Manager")
                .description("Manage tags at scale")
                .category("Content")
                .parameters(List.of(
                        io.cxforge.admintools.model.ToolParameter.builder()
                                .name("rootPath")
                                .label("Root Path")
                                .type(io.cxforge.admintools.model.ToolParameter.ParameterType.PATH)
                                .required(true)
                                .build()
                ))
                .build();

        when(toolRegistry.getAllTools()).thenReturn(List.of(tool));

        mockMvc.perform(get("/api/v1/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].parameters", hasSize(1)))
                .andExpect(jsonPath("$[0].parameters[0].name", is("rootPath")))
                .andExpect(jsonPath("$[0].parameters[0].required", is(true)));
    }
}
