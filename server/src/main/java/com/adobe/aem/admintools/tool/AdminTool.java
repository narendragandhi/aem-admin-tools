package com.adobe.aem.admintools.tool;

import com.adobe.aem.admintools.model.Job;
import com.adobe.aem.admintools.model.ToolDefinition;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Interface for all admin tools. Implement this to create a new tool.
 */
public interface AdminTool {

    /**
     * Returns the tool definition including metadata and parameters.
     */
    ToolDefinition getDefinition();

    /**
     * Validates the parameters before running.
     * @param parameters The input parameters
     * @return null if valid, error message otherwise
     */
    String validateParameters(Map<String, Object> parameters);

    /**
     * Executes the tool with the given job context.
     * Update job progress and results as you go.
     * Call progressCallback.accept(job) to emit progress updates.
     * @param job The job to execute
     * @param progressCallback Callback to emit progress updates
     */
    void execute(Job job, Consumer<Job> progressCallback);

    /**
     * Returns the unique tool ID.
     */
    default String getId() {
        return getDefinition().getId();
    }
}
