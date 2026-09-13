package com.jobpilot.infrastructure.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.redis")
public class JobPilotRedisProperties {

    /**
     * When {@code false}, Redis auto-connection is skipped, cache is an in-memory
     * {@code ConcurrentMapCacheManager}, and rate limiting uses a concurrent map.
     * Tests set this to {@code false}.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
