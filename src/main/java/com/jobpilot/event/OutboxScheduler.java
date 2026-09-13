package com.jobpilot.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "jobpilot.outbox.relay",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxProcessor outboxProcessor;

    public OutboxScheduler(OutboxProcessor outboxProcessor) {
        this.outboxProcessor = outboxProcessor;
    }

    @Scheduled(fixedDelayString = "${jobpilot.outbox.relay.fixed-delay-ms:5000}")
    public void tick() {
        int published = outboxProcessor.processPendingBatch();
        if (published > 0) {
            log.info("Outbox relay published {} event(s)", published);
        }
    }
}
