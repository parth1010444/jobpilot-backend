package com.jobpilot.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for future domain modules. Phase 1 has no business rules yet.
 */
public class JobPilotException extends RuntimeException {

    private final HttpStatus status;

    public JobPilotException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public JobPilotException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
