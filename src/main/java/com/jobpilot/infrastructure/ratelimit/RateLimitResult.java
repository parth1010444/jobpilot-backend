package com.jobpilot.infrastructure.ratelimit;

public record RateLimitResult(long count, long retryAfterSeconds) {
}
