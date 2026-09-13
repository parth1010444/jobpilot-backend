package com.jobpilot.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.reminders")
public class ReminderProperties {

    private final Scheduler scheduler = new Scheduler();

    public Scheduler getScheduler() {
        return scheduler;
    }

    public static class Scheduler {
        /**
         * When false, {@link ReminderScheduler} does not run (tests call the processor directly).
         */
        private boolean enabled = true;

        /**
         * Fixed delay between scheduler ticks in milliseconds (default: 1 minute).
         */
        private long fixedDelayMs = 60_000L;

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
    }
}
