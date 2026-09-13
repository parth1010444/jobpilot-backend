package com.jobpilot.common.error;

import java.time.Instant;

/**
 * Standard API error payload returned by {@link GlobalExceptionHandler}.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
