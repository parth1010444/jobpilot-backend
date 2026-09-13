package com.jobpilot.event;

import com.jobpilot.event.dto.ApplicationStatusChangedEvent;

/**
 * Port for writing domain events into the transactional outbox.
 * Implementations must participate in the caller's {@code @Transactional} boundary.
 */
public interface OutboxPublisher {

    void publishApplicationStatusChanged(ApplicationStatusChangedEvent event);
}
