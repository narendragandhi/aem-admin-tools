package io.cxforge.admintools.tool;

import io.cxforge.admintools.model.*;
import io.cxforge.admintools.service.AemClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContentHealthCheckToolTest {

    private ContentHealthCheckTool tool;

    @Mock
    private AemClientService aemClientService;

    @Mock
    private Job mockJob;

    @Mock
    private Consumer<Job> mockProgressCallback;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        tool = new ContentHealthCheckTool(aemClientService);
    }

    @Test
    @DisplayName("Tool definition has correct metadata")
    void testGetDefinition() {
        ToolDefinition definition = tool.getDefinition();

        assertNotNull(definition);
        assertEquals("content-health-check", definition.getId());
        assertEquals("Content Health Check", definition.getName());
        assertEquals("Content", definition.getCategory());
        assertEquals("health", definition.getIcon());
        assertFalse(definition.isDestructive());
        assertTrue(definition.isRequiresAem());

        // Check parameters
        assertNotNull(definition.getParameters());
        assertEquals(4, definition.getParameters().size());

        // Verify rootPath parameter
        ToolParameter rootPath = definition.getParameters().stream()
                .filter(p -> "rootPath".equals(p.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(rootPath.isRequired());
        assertEquals(ToolParameter.ParameterType.PATH, rootPath.getType());
    }

    @Test
    @DisplayName("Validation succeeds with valid content path")
    void testValidateParameters_validPath() {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/mysite");

        String result = tool.validateParameters(params);
        assertNull(result);
    }

    @Test
    @DisplayName("Validation fails when root path is missing")
    void testValidateParameters_missingPath() {
        Map<String, Object> params = new HashMap<>();

        String result = tool.validateParameters(params);
        assertEquals("Root path is required", result);
    }

    @Test
    @DisplayName("Validation fails when root path is empty")
    void testValidateParameters_emptyPath() {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "");

        String result = tool.validateParameters(params);
        assertEquals("Root path is required", result);
    }

    @Test
    @DisplayName("Validation fails when root path doesn't start with /content")
    void testValidateParameters_invalidPath() {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/apps/mysite");

        String result = tool.validateParameters(params);
        assertEquals("Root path must start with /content", result);
    }

    @Test
    @DisplayName("Execute logs error when AEM is not enabled")
    void testExecute_aemNotEnabled() {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/we-retail");
        params.put("checks", List.of("missing-title"));

        when(mockJob.getParameters()).thenReturn(params);
        when(aemClientService.isEnabled()).thenReturn(false);

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addLog(eq(JobLogEntry.Level.ERROR), contains("AEM connection is not enabled"));
    }

    @Test
    @DisplayName("Execute warns when no pages found")
    void testExecute_noPagesFound() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/empty-site");
        params.put("checks", List.of("missing-title"));
        params.put("maxPages", 100);

        when(mockJob.getParameters()).thenReturn(params);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(eq("/content/empty-site"), eq(100)))
                .thenReturn(Collections.emptyList());

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addLog(eq(JobLogEntry.Level.WARN), contains("No pages found"));
    }

    @Test
    @DisplayName("Execute detects missing title")
    void testExecute_detectsMissingTitle() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-title"));
        params.put("maxPages", 100);

        Map<String, Object> jcrContent = new HashMap<>();
        // No jcr:title present

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/page1");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("Missing page title")));
    }

    @Test
    @DisplayName("Execute detects missing description")
    void testExecute_detectsMissingDescription() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-description"));
        params.put("maxPages", 100);

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:title", "Page Title");
        // No jcr:description present

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/page1");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("Missing meta description")));
    }

    @Test
    @DisplayName("Execute detects stale content")
    void testExecute_detectsStaleContent() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("stale-content"));
        params.put("staleDays", 30);
        params.put("maxPages", 100);

        // Content last modified 60 days ago
        Instant staleDate = Instant.now().minus(60, ChronoUnit.DAYS);

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:title", "Old Page");
        jcrContent.put("cq:lastModified", staleDate.toEpochMilli());

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/old-page");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("Content is stale")));
    }

    @Test
    @DisplayName("Execute detects unpublished changes")
    void testExecute_detectsUnpublishedChanges() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("unpublished-changes"));
        params.put("maxPages", 100);

        // Modified after last publish
        Instant lastModified = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant lastPublished = Instant.now().minus(7, ChronoUnit.DAYS);

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:title", "Modified Page");
        jcrContent.put("cq:lastModified", lastModified.toEpochMilli());
        jcrContent.put("cq:lastReplicated", lastPublished.toEpochMilli());

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/modified-page");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("unpublished changes")));
    }

    @Test
    @DisplayName("Execute reports success for healthy content")
    void testExecute_healthyContent() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-title", "missing-description"));
        params.put("maxPages", 100);

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:title", "Good Page");
        jcrContent.put("jcr:description", "This is a well-configured page");

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/good-page");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.SUCCESS &&
                result.getMessage().contains("No issues found")));
    }

    @Test
    @DisplayName("Execute stops when job is cancelled")
    void testExecute_cancelledJob() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-title"));
        params.put("maxPages", 100);

        Map<String, Object> page1 = new HashMap<>();
        page1.put("jcr:path", "/content/site/page1");
        page1.put("jcr:content", new HashMap<>());

        Map<String, Object> page2 = new HashMap<>();
        page2.put("jcr:path", "/content/site/page2");
        page2.put("jcr:content", new HashMap<>());

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.CANCELLED);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page1, page2));

        tool.execute(mockJob, mockProgressCallback);

        // Should log warning about cancellation
        verify(mockJob).addLog(eq(JobLogEntry.Level.WARN), contains("cancelled"));
        // Should not process any pages
        verify(mockJob, never()).addResult(any());
    }

    @Test
    @DisplayName("Execute handles AEM query failure gracefully")
    void testExecute_aemQueryFailure() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-title"));
        params.put("maxPages", 100);

        when(mockJob.getParameters()).thenReturn(params);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt()))
                .thenThrow(new RuntimeException("Connection timeout"));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addLog(eq(JobLogEntry.Level.ERROR), contains("Failed to query AEM"));
    }

    @Test
    @DisplayName("Execute handles page processing error gracefully")
    void testExecute_pageProcessingError() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("missing-title"));
        params.put("maxPages", 100);

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/bad-page");
        // No jcr:content - will try to fetch and fail

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));
        when(aemClientService.getPageProperties(anyString()))
                .thenThrow(new RuntimeException("Page not accessible"));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("Error scanning page")));
    }

    @Test
    @DisplayName("Execute detects never-published pages")
    void testExecute_detectsNeverPublished() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("rootPath", "/content/site");
        params.put("checks", List.of("unpublished-changes"));
        params.put("maxPages", 100);

        Map<String, Object> jcrContent = new HashMap<>();
        jcrContent.put("jcr:title", "New Page");
        jcrContent.put("cq:lastModified", Instant.now().toEpochMilli());
        // No cq:lastReplicated - never published

        Map<String, Object> page = new HashMap<>();
        page.put("jcr:path", "/content/site/new-page");
        page.put("jcr:content", jcrContent);

        when(mockJob.getParameters()).thenReturn(params);
        when(mockJob.getStatus()).thenReturn(JobStatus.RUNNING);
        when(aemClientService.isEnabled()).thenReturn(true);
        when(aemClientService.findPages(anyString(), anyInt())).thenReturn(List.of(page));

        tool.execute(mockJob, mockProgressCallback);

        verify(mockJob).addResult(argThat(result ->
                result.getStatus() == JobResult.ResultStatus.ERROR &&
                result.getMessage().contains("never been published")));
    }
}
