package gr.ntua.multimedia.exception;

public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) { super(message); }
    public PermissionDeniedException(String message, Throwable cause) { super(message, cause); }
}