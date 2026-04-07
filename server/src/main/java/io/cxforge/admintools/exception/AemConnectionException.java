package io.cxforge.admintools.exception;

public class AemConnectionException extends RuntimeException {
    public AemConnectionException(String message) {
        super(message);
    }

    public AemConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
