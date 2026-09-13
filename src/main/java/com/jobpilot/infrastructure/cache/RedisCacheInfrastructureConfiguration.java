package com.jobpilot.infrastructure.cache;

import com.jobpilot.infrastructure.ratelimit.RateLimitStore;
import com.jobpilot.infrastructure.ratelimit.RedisRateLimitStore;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * Redis connection, {@link RedisCacheManager}, and Redis-backed rate-limit store.
 * Only active when {@code jobpilot.redis.enabled=true}. Domain packages stay free
 * of Redis types.
 */
@Configuration
@ConditionalOnProperty(name = "jobpilot.redis.enabled", havingValue = "true")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisCacheInfrastructureConfiguration {

    @Bean
    LettuceConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration();
        standalone.setHostName(properties.getHost());
        standalone.setPort(properties.getPort());
        standalone.setDatabase(properties.getDatabase());
        if (StringUtils.hasText(properties.getUsername())) {
            standalone.setUsername(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            standalone.setPassword(properties.getPassword());
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder client = LettuceClientConfiguration.builder();
        if (properties.getTimeout() != null) {
            client.commandTimeout(properties.getTimeout());
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone, client.build());
        factory.setValidateConnection(false);
        return factory;
    }

    @Bean
    StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        return new StringRedisTemplate(redisConnectionFactory);
    }

    @Bean
    CacheManager cacheManager(
            LettuceConnectionFactory redisConnectionFactory,
            JobPilotCacheProperties cacheProperties
    ) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .entryTtl(cacheProperties.getRecommendationsTtl());

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put(CacheNames.RECOMMENDATIONS, defaults.entryTtl(cacheProperties.getRecommendationsTtl()));
        perCache.put(CacheNames.APPLICATION_MATCH, defaults.entryTtl(cacheProperties.getApplicationMatchTtl()));
        perCache.put(CacheNames.ANALYTICS, defaults.entryTtl(cacheProperties.getAnalyticsTtl()));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .initialCacheNames(Set.of(
                        CacheNames.RECOMMENDATIONS,
                        CacheNames.APPLICATION_MATCH,
                        CacheNames.ANALYTICS
                ))
                .build();
    }

    @Bean
    RateLimitStore rateLimitStore(StringRedisTemplate stringRedisTemplate, Clock clock) {
        return new RedisRateLimitStore(stringRedisTemplate, clock);
    }
}
