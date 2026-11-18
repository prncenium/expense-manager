package com.expensemanager.assistant;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    /** A clock the test moves by hand. */
    static class ManualClock extends Clock {
        long millis = 1_000_000;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }

    @Test
    void allowsTheLimitPerMinuteThenBlocksUntilTheNextMinute() {
        ManualClock clock = new ManualClock();
        RateLimiter limiter = new RateLimiter(clock, 3);
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();

        clock.millis += 59_999;
        assertThat(limiter.tryAcquire()).isFalse();
        clock.millis += 1;
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
