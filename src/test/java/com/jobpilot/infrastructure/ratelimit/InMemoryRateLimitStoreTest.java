package com.jobpilot.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryRateLimitStoreTest {

    @Test
    void incrementsWithinWindowThenResetsOnNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-13T12:00:00Z"));
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(clock);
        Duration window = Duration.ofMinutes(1);

        assertThat(store.increment("ip:1.1.1.1", window).count()).isEqualTo(1);
        assertThat(store.increment("ip:1.1.1.1", window).count()).isEqualTo(2);
        assertThat(store.increment("ip:2.2.2.2", window).count()).isEqualTo(1);

        clock.setInstant(Instant.parse("2026-09-13T12:01:00Z"));
        assertThat(store.increment("ip:1.1.1.1", window).count()).isEqualTo(1);
    }

    @Test
    void retryAfterIsAtLeastOneSecond() {
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(Clock.systemUTC());
        RateLimitResult result = store.increment("k", Duration.ofMinutes(1));
        assertThat(result.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
