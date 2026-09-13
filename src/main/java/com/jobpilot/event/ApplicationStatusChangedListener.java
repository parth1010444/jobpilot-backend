package com.jobpilot.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.event.dto.ApplicationStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Skeleton consumer for {@link ApplicationStatusChangedEvent}.
 *
 * <p><b>Idempotency:</b> delivery is at-least-once, so this listener (and future handlers)
 * must tolerate duplicates. A processed-events table keyed by outbox event id / a business
 * idempotency key can be added later; for v1 we only log and structure the entry point.
 */
@Component
@ConditionalOnProperty(
        prefix = "jobpilot.kafka.consumer",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ApplicationStatusChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStatusChangedListener.class);

    private final ObjectMapper objectMapper;

    public ApplicationStatusChangedListener(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${jobpilot.outbox.application-events-topic:jobpilot.application.events}",
            groupId = "${jobpilot.kafka.consumer.group-id:jobpilot-backend}"
    )
    public void onMessage(String payload) {
        try {
            ApplicationStatusChangedEvent event =
                    objectMapper.readValue(payload, ApplicationStatusChangedEvent.class);
            // Future: check processed-events table by (applicationId, occurredAt) or event id.
            log.info(
                    "Received ApplicationStatusChanged applicationId={} userId={} {} -> {}",
                    event.applicationId(),
                    event.userId(),
                    event.fromStatus(),
                    event.toStatus()
            );
        } catch (Exception ex) {
            log.warn("Failed to deserialize ApplicationStatusChanged payload: {}", ex.getMessage());
            throw new IllegalStateException("Invalid ApplicationStatusChanged payload", ex);
        }
    }
}
