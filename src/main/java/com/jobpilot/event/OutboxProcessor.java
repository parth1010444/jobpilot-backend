package com.jobpilot.event;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays PENDING outbox rows to Kafka.
 *
 * <p><b>Delivery semantics: at-least-once.</b> The relay sends to Kafka first, then marks
 * the row PUBLISHED. If the process crashes after a successful send but before the status
 * update, the next tick will republish the same event. Consumers must be idempotent.
 *
 * <p>On send failure the row stays PENDING (attempts incremented + last_error). After
 * {@code jobpilot.outbox.relay.max-attempts} failures it becomes FAILED.
 */
@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxProperties outboxProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            OutboxProperties outboxProperties,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxProperties = outboxProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Transactional
    public int processPendingBatch() {
        int batchSize = outboxProperties.getRelay().getBatchSize();
        List<OutboxEvent> pending = outboxEventRepository.findPending(PageRequest.of(0, batchSize));
        int published = 0;
        for (OutboxEvent event : pending) {
            if (processOne(event)) {
                published++;
            }
        }
        return published;
    }

    /**
     * @return true if this call successfully published and marked the row PUBLISHED
     */
    @Transactional
    public boolean processOne(OutboxEvent event) {
        Instant now = clock.instant();
        int maxAttempts = outboxProperties.getRelay().getMaxAttempts();

        try {
            kafkaTemplate
                    .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
                    .get(10, TimeUnit.SECONDS);

            int updated = outboxEventRepository.markPublished(event.getId(), now);
            if (updated == 0) {
                log.debug("Outbox event {} already published or not PENDING — skip mark", event.getId());
                return false;
            }
            log.info(
                    "Published outbox event {} type={} topic={}",
                    event.getId(),
                    event.getEventType(),
                    event.getTopic()
            );
            return true;
        } catch (Exception ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            // Re-load in case clearAutomatically/detached state after a prior update in the batch
            OutboxEvent managed = outboxEventRepository.findById(event.getId()).orElse(event);
            if (managed.getStatus() == OutboxEventStatus.PENDING) {
                managed.recordFailure(message, maxAttempts);
                outboxEventRepository.save(managed);
            }
            log.warn(
                    "Failed to publish outbox event {} (will retry unless FAILED): {}",
                    event.getId(),
                    message
            );
            return false;
        }
    }
}
