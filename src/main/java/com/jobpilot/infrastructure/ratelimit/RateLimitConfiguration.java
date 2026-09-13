package com.jobpilot.infrastructure.ratelimit;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfiguration {

    /**
     * Keep the filter off the container's generic chain so it runs exactly once,
     * after JWT, inside {@code SecurityFilterChain}.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "jobpilot.redis.enabled", havingValue = "false", matchIfMissing = true)
    RateLimitStore inMemoryRateLimitStore(Clock clock) {
        return new InMemoryRateLimitStore(clock);
    }
}
