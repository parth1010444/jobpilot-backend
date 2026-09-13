package com.jobpilot.infrastructure.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class CacheEvictionService implements CacheEviction {

    private final CacheManager cacheManager;

    public CacheEvictionService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void evictUserRecommendations(UUID userId) {
        evictByPrefix(CacheNames.RECOMMENDATIONS, userId.toString());
    }

    @Override
    public void evictApplicationMatch(UUID userId, UUID applicationId) {
        Cache cache = cacheManager.getCache(CacheNames.APPLICATION_MATCH);
        if (cache != null) {
            cache.evict(CacheNames.applicationMatchKey(userId, applicationId));
        }
    }

    @Override
    public void evictUserApplicationMatches(UUID userId) {
        evictByPrefix(CacheNames.APPLICATION_MATCH, userId.toString());
    }

    @Override
    public void evictUserAnalytics(UUID userId) {
        evictByPrefix(CacheNames.ANALYTICS, userId.toString());
    }

    /**
     * ConcurrentMap (test / Redis-disabled): drop keys that start with the user id.
     * Redis / unknown native cache: {@link Cache#clear()} the whole name. TTLs are
     * short (60–300s) so a full clear is acceptable when prefix scan is unavailable.
     */
    private void evictByPrefix(String cacheName, String prefix) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return;
        }
        Object nativeCache = cache.getNativeCache();
        if (nativeCache instanceof ConcurrentMap<?, ?> map) {
            List<Object> keys = new ArrayList<>();
            for (Object key : map.keySet()) {
                if (key != null && key.toString().startsWith(prefix)) {
                    keys.add(key);
                }
            }
            keys.forEach(cache::evict);
            return;
        }
        cache.clear();
    }
}
