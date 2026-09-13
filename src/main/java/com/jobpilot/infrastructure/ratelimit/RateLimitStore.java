package com.jobpilot.infrastructure.ratelimit;

import java.time.Duration;

/**
 * Fixed-window counter. Redis-backed when Redis is enabled; otherwise a
 * process-local concurrent map (tests / local without Redis).
 */
public interface RateLimitStore {

    RateLimitResult increment(String key, Duration window);
}
