package com.jobpilot.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.event.dto.ApplicationStatusChangedEvent;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inserts PENDING outbox rows in the same transaction as the domain change.
 * Does not talk to Kafka — the {@link OutboxProcessor} relay publishes later (at-least-once).
 */
@Service
public class OutboxService implements OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            OutboxProperties outboxProperties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxProperties = outboxProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void publishApplicationStatusChanged(ApplicationStatusChangedEvent event) {
        Instant now = clock.instant();
        String payload = toJson(event);
        String topic = outboxProperties.getApplicationEventsTopic();
        String partitionKey = event.applicationId().toString();

        OutboxEvent row = new OutboxEvent(
                ApplicationStatusChangedEvent.AGGREGATE_TYPE,
                event.applicationId(),
                ApplicationStatusChangedEvent.EVENT_TYPE,
                payload,
                topic,
                partitionKey,
                now
        );
        outboxEventRepository.save(row);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
