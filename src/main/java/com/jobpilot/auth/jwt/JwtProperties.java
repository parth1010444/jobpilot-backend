package com.jobpilot.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobpilot.jwt")
public class JwtProperties {

    /**
     * HMAC secret. Local/test defaults are unsafe — production must override via JOBPILOT_JWT_SECRET.
     */
    private String secret;

    private long expirationMs = 86_400_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
