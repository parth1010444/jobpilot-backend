package com.jobpilot.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.cors")
public class CorsProperties {

    /**
     * Browser origins allowed to call the API. Defaults cover a local Vite
     * frontend ({@code http://localhost:5173} and {@code http://127.0.0.1:5173}).
     * Production must override via {@code JOBPILOT_CORS_ALLOWED_ORIGINS}.
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    ));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = new ArrayList<>(allowedOrigins != null ? allowedOrigins : List.of());
    }
}
