package com.expensemanager.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Fixed one-minute window: one counter and one timestamp (ASSISTANT-SPEC §3.12), so it costs no
 * memory however many requests come in. Protects the free AI quota.
 */
@Component
public class RateLimiter {

    static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final int maxPerMinute;
    private long windowStart;
    private int count;

    public RateLimiter(Clock clock, @Value("${app.assistant.max-per-minute:30}") int maxPerMinute) {
        this.clock = clock;
        this.maxPerMinute = maxPerMinute;
    }

    /** True when one more request fits in the current minute. */
    public synchronized boolean tryAcquire() {
        long now = clock.millis();
        if (now - windowStart >= WINDOW_MILLIS) {
            windowStart = now;
            count = 0;
        }
        if (count >= maxPerMinute) {
            return false;
        }
        count++;
        return true;
    }
}
