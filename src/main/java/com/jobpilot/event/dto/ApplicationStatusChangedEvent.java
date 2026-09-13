package com.jobpilot.event.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event payload emitted when an application status changes (create or transition).
 * Jackson-friendly record for JSON serialization into the outbox payload column.
 */
public record ApplicationStatusChangedEvent(
        UUID applicationId,
        UUID userId,
        String fromStatus,
        String toStatus,
        Instant occurredAt
) {
    public static final String EVENT_TYPE = "APPLICATION_STATUS_CHANGED";
    public static final String AGGREGATE_TYPE = "APPLICATION";
}
