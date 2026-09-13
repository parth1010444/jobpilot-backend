package com.jobpilot.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.outbox")
public class OutboxProperties {

    /**
     * Kafka topic for application domain events.
     */
    private String applicationEventsTopic = "jobpilot.application.events";

    private final Relay relay = new Relay();

    public String getApplicationEventsTopic() {
        return applicationEventsTopic;
    }

    public void setApplicationEventsTopic(String applicationEventsTopic) {
        this.applicationEventsTopic = applicationEventsTopic;
    }

    public Relay getRelay() {
        return relay;
    }

    public static class Relay {
        /**
         * When false, {@link OutboxScheduler} does not run (tests leave rows PENDING).
         */
        private boolean enabled = true;

        /**
         * Fixed delay between relay ticks in milliseconds (default: 5 seconds).
         */
        private long fixedDelayMs = 5_000L;

        /**
         * Max PENDING rows to claim per tick.
         */
        private int batchSize = 50;

        /**
         * After this many failed publish attempts the row becomes FAILED.
         */
        private int maxAttempts = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }
}
