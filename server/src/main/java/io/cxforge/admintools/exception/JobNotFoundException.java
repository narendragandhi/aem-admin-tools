package io.cxforge.admintools.exception;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }
}
