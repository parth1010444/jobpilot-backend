package com.jobpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.openapi")
public class OpenApiProperties {

    /**
     * When {@code true}, Swagger UI and {@code /v3/api-docs} are public and
     * springdoc is enabled. Disable on the {@code prod} profile.
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
