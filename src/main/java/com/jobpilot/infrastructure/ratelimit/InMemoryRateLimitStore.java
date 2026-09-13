package com.jobpilot.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local fixed-window counters. Used when Redis is disabled so tests
 * still exercise HTTP 429 without a broker.
 */
public class InMemoryRateLimitStore implements RateLimitStore {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public RateLimitResult increment(String key, Duration window) {
        long nowMs = clock.millis();
        long windowMs = Math.max(1L, window.toMillis());
        long windowStart = (nowMs / windowMs) * windowMs;
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new Window(windowStart, 1L);
            }
            return new Window(existing.windowStart, existing.count + 1L);
        });
        long retryAfter = Math.max(1L, (windowStart + windowMs - nowMs + 999L) / 1000L);
        return new RateLimitResult(updated.count, retryAfter);
    }

    void reset() {
        windows.clear();
    }

    private record Window(long windowStart, long count) {
    }
}
