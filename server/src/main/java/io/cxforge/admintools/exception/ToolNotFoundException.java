package io.cxforge.admintools.exception;

public class ToolNotFoundException extends RuntimeException {
    public ToolNotFoundException(String toolId) {
        super("Tool not found: " + toolId);
    }
}
