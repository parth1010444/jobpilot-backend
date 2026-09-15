package com.jobpilot.config;

import com.jobpilot.auth.jwt.JwtProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fail closed on the {@code prod} profile if the JWT HMAC secret is missing,
 * still the local default, or shorter than 32 characters.
 */
@Component
@Profile("prod")
public class ProductionSecurityValidator {

    static final String LOCAL_DEFAULT_MARKER = "local-dev-only";
    static final int MIN_SECRET_LENGTH = 32;

    private final JwtProperties jwtProperties;

    public ProductionSecurityValidator(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void validate() {
        validateSecret(jwtProperties.getSecret());
    }

    static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Production requires JOBPILOT_JWT_SECRET. "
                            + "Generate one with: openssl rand -base64 48"
            );
        }
        if (secret.contains(LOCAL_DEFAULT_MARKER) || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Production JOBPILOT_JWT_SECRET must be a long random value "
                            + "(at least " + MIN_SECRET_LENGTH + " characters) "
                            + "and must not use the local development default."
            );
        }
    }
}
