package com.jobpilot.infrastructure;

import com.jobpilot.infrastructure.cache.JobPilotCacheProperties;
import com.jobpilot.infrastructure.cache.JobPilotRedisProperties;
import com.jobpilot.infrastructure.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
@EnableConfigurationProperties({
        JobPilotRedisProperties.class,
        JobPilotCacheProperties.class,
        RateLimitProperties.class
})
public class InfrastructureModuleConfiguration {
}
