package com.jobpilot.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class CacheEvictionServiceTest {

    private ConcurrentMapCacheManager cacheManager;
    private CacheEvictionService eviction;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(
                CacheNames.RECOMMENDATIONS,
                CacheNames.APPLICATION_MATCH,
                CacheNames.ANALYTICS
        );
        eviction = new CacheEvictionService(cacheManager);
    }

    @Test
    void evictsOnlyThatUsersRecommendationKeys() {
        UUID user = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID other = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Cache cache = cacheManager.getCache(CacheNames.RECOMMENDATIONS);
        cache.put(CacheNames.recommendationsKey(user, 10), "mine");
        cache.put(CacheNames.recommendationsKey(user, 5), "mine-5");
        cache.put(CacheNames.recommendationsKey(other, 10), "theirs");

        eviction.evictUserRecommendations(user);

        assertThat(cache.get(CacheNames.recommendationsKey(user, 10))).isNull();
        assertThat(cache.get(CacheNames.recommendationsKey(user, 5))).isNull();
        assertThat(cache.get(CacheNames.recommendationsKey(other, 10)).get()).isEqualTo("theirs");
    }

    @Test
    void evictsSingleApplicationMatch() {
        UUID user = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        UUID otherApp = UUID.randomUUID();
        Cache cache = cacheManager.getCache(CacheNames.APPLICATION_MATCH);
        cache.put(CacheNames.applicationMatchKey(user, app), 42);
        cache.put(CacheNames.applicationMatchKey(user, otherApp), 7);

        eviction.evictApplicationMatch(user, app);

        assertThat(cache.get(CacheNames.applicationMatchKey(user, app))).isNull();
        assertThat(cache.get(CacheNames.applicationMatchKey(user, otherApp)).get()).isEqualTo(7);
    }

    @Test
    void evictsOnlyThatUsersAnalyticsKeys() {
        UUID user = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID other = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Cache cache = cacheManager.getCache(CacheNames.ANALYTICS);
        cache.put(user + ":summary", "mine");
        cache.put(user + ":funnel", "mine-funnel");
        cache.put(other + ":summary", "theirs");

        eviction.evictUserAnalytics(user);

        assertThat(cache.get(user + ":summary")).isNull();
        assertThat(cache.get(user + ":funnel")).isNull();
        assertThat(cache.get(other + ":summary").get()).isEqualTo("theirs");
    }
}
