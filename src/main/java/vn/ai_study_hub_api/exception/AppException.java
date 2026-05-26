package vn.ai_study_hub_api.exception;

import org.springframework.http.HttpStatus;

/**
 * Custom base runtime exception for the application, supporting HTTP Status mapping.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
