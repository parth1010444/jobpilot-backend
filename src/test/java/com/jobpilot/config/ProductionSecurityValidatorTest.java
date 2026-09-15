package com.jobpilot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductionSecurityValidatorTest {

    @Test
    void rejectsNullBlankAndLocalDefaultSecrets() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecret(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JOBPILOT_JWT_SECRET");
        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecret("   "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecret(
                "local-dev-only-change-me-use-a-long-random-secret-at-least-32-bytes"
        )).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ProductionSecurityValidator.validateSecret("short-secret"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsALongRandomSecret() {
        assertThatCode(() -> ProductionSecurityValidator.validateSecret(
                "a-sufficiently-long-random-production-secret!!"
        )).doesNotThrowAnyException();
    }
}
