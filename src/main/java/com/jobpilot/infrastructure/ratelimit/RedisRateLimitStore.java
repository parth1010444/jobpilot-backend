package com.jobpilot.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Fixed-window counters in Redis. {@code INCR} + {@code PEXPIRE} on first hit
 * (atomic via Lua) so a crash cannot leave a key without a TTL.
 */
public class RedisRateLimitStore implements RateLimitStore {

    private static final DefaultRedisScript<Long> INCR_EXPIRE = new DefaultRedisScript<>();

    static {
        INCR_EXPIRE.setResultType(Long.class);
        INCR_EXPIRE.setScriptText("""
                local current = redis.call('INCR', KEYS[1])
                if current == 1 then
                  redis.call('PEXPIRE', KEYS[1], ARGV[1])
                end
                return current
                """);
    }

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    @Override
    public RateLimitResult increment(String key, Duration window) {
        long nowMs = clock.millis();
        long windowMs = Math.max(1L, window.toMillis());
        long windowStart = (nowMs / windowMs) * windowMs;
        String redisKey = "rl:" + key + ":" + windowStart;
        Long count = redisTemplate.execute(INCR_EXPIRE, List.of(redisKey), String.valueOf(windowMs));
        long current = count != null ? count : 1L;
        long retryAfter = Math.max(1L, (windowStart + windowMs - nowMs + 999L) / 1000L);
        return new RateLimitResult(current, retryAfter);
    }
}
