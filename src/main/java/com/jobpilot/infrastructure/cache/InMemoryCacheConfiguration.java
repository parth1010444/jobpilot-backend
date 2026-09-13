package com.jobpilot.infrastructure.cache;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "jobpilot.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryCacheConfiguration {

    @Bean
    CacheManager cacheManager() {
        ConcurrentMapCacheManager manager = new ConcurrentMapCacheManager(
                CacheNames.RECOMMENDATIONS,
                CacheNames.APPLICATION_MATCH,
                CacheNames.ANALYTICS
        );
        manager.setAllowNullValues(false);
        return manager;
    }
}
