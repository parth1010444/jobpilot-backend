package com.jobpilot.infrastructure.cache;

import java.util.UUID;

/**
 * Domain-facing cache invalidation. Implementations talk to Spring's
 * {@link org.springframework.cache.CacheManager} only.
 *
 * <p>Eviction map (Phase 10):
 * <ul>
 *   <li>Application create / update / delete → {@code recommendations} for that user</li>
 *   <li>Application delete, or jobDescription change → {@code applicationMatch} for that pair</li>
 *   <li>Skill add / delete → {@code recommendations} and all {@code applicationMatch} for that user</li>
 *   <li>Analyze (and first-time persist on match GET) → that {@code applicationMatch} + user recommendations</li>
 *   <li>Interview create / update / delete → {@code recommendations} for that user</li>
 * </ul>
 *
 * <p>When Redis is disabled, an in-memory {@code ConcurrentMapCacheManager} still
 * honors these evictions so tests exercise the same behavior.
 */
public interface CacheEviction {

    void evictUserRecommendations(UUID userId);

    void evictApplicationMatch(UUID userId, UUID applicationId);

    void evictUserApplicationMatches(UUID userId);
}
