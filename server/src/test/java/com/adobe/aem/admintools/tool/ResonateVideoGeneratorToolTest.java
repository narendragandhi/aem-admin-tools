package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.Job;
import com.adobe.aem.admintools.model.JobLogEntry;
import com.adobe.aem.admintools.model.JobResult;
import com.adobe.aem.admintools.model.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResonateVideoGeneratorToolTest {

    private ResonateVideoGeneratorTool tool;

    @Mock
    private Job mockJob;

    @Mock
    private Consumer<Job> mockProgressCallback;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new ResonateVideoGeneratorTool();
    }

    @Test
    void testGetDefinition() {
        ToolDefinition definition = tool.getDefinition();
        assertNotNull(definition);
        assertEquals("resonate-video-generator", definition.getId());
        assertEquals("Resonate Video Generator", definition.getName());
        assertEquals("Generates a video based on a text prompt.", definition.getDescription());
        assertEquals("Content", definition.getCategory());
        assertEquals("movie", definition.getIcon());
        assertFalse(definition.isDestructive());
        assertFalse(definition.isRequiresAem());
        assertNotNull(definition.getParameters());
        assertEquals(1, definition.getParameters().size());
        assertEquals("videoPrompt", definition.getParameters().get(0).getName());
    }

    @Test
    void testValidateParameters_valid() {
        Map<String, Object> params = new HashMap<>();
        params.put("videoPrompt", "A cat playing piano");
        String result = tool.validateParameters(params);
        assertNull(result);
    }

    @Test
    void testValidateParameters_emptyPrompt() {
        Map<String, Object> params = new HashMap<>();
        params.put("videoPrompt", "");
        String result = tool.validateParameters(params);
        assertEquals("Video prompt cannot be empty.", result);
    }

    @Test
    void testValidateParameters_nullPrompt() {
        Map<String, Object> params = new HashMap<>();
        params.put("videoPrompt", null);
        String result = tool.validateParameters(params);
        assertEquals("Video prompt cannot be empty.", result);
    }

    @Test
    void testExecute_success() {
        Map<String, Object> params = new HashMap<>();
        params.put("videoPrompt", "A dog chasing a ball");
        when(mockJob.getParameters()).thenReturn(params);

        tool.execute(mockJob, mockProgressCallback);

        // Verify logs
        ArgumentCaptor<String> logMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockJob, atLeastOnce()).addLog(any(JobLogEntry.Level.class), logMessageCaptor.capture());
        List<String> capturedLogMessages = logMessageCaptor.getAllValues();
        assertTrue(capturedLogMessages.stream().anyMatch(log -> log.contains("Starting video generation")));
        assertTrue(capturedLogMessages.stream().anyMatch(log -> log.contains("Video generated")));

        // Verify result
        ArgumentCaptor<JobResult> resultCaptor = ArgumentCaptor.forClass(JobResult.class);
        verify(mockJob).addResult(resultCaptor.capture());
        JobResult capturedResult = resultCaptor.getValue();
        assertEquals(JobResult.ResultStatus.SUCCESS, capturedResult.getStatus());
        assertTrue(capturedResult.getPath().startsWith("https://example.com/generated-video-"));
        assertEquals("Video generated successfully!", capturedResult.getMessage());
        assertNotNull(capturedResult.getDetails());
        assertTrue(capturedResult.getDetails().containsKey("url"));
        assertTrue(capturedResult.getDetails().containsKey("prompt"));
        assertEquals("A dog chasing a ball", capturedResult.getDetails().get("prompt"));

        // Verify progress callback
        verify(mockProgressCallback).accept(mockJob);
    }

    // Skipping testExecute_interrupted for now due to complexity of mocking Thread.sleep
}
