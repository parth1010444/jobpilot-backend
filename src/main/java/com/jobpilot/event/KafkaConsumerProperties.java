package com.jobpilot.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.kafka.consumer")
public class KafkaConsumerProperties {

    /**
     * When false, {@link ApplicationStatusChangedListener} is not registered.
     * Default true for local/prod; tests set false.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
