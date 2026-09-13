package com.jobpilot.infrastructure.cache;

import java.util.UUID;

/**
 * Spring Cache names and key helpers. Domain services depend on these constants
 * and {@link CacheEviction} only — never on Redis types.
 *
 * <p>Keys are strings so in-memory ({@code ConcurrentMapCacheManager}) and Redis
 * backends share the same eviction prefix rules.
 *
 * <ul>
 *   <li>{@code recommendations} — {@code userId:limit} (limit is normalized)</li>
 *   <li>{@code applicationMatch} — {@code userId:applicationId}</li>
 * </ul>
 */
public final class CacheNames {

    public static final String RECOMMENDATIONS = "recommendations";
    public static final String APPLICATION_MATCH = "applicationMatch";

    static final int DEFAULT_RECOMMENDATION_LIMIT = 10;
    static final int MAX_RECOMMENDATION_LIMIT = 100;

    private CacheNames() {
    }

    public static String recommendationsKey(UUID userId, Integer limit) {
        int effective = DEFAULT_RECOMMENDATION_LIMIT;
        if (limit != null) {
            if (limit < 1) {
                effective = DEFAULT_RECOMMENDATION_LIMIT;
            } else {
                effective = Math.min(limit, MAX_RECOMMENDATION_LIMIT);
            }
        }
        return userId + ":" + effective;
    }

    public static String applicationMatchKey(UUID userId, UUID applicationId) {
        return userId + ":" + applicationId;
    }
}
