package com.nf.noticeforecast.global.ratelimit;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

public class FixedWindowRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private final Clock clock;
    private final int limit;
    private final Map<String, Integer> counts = new HashMap<>();
    private long currentWindow = -1;

    public FixedWindowRateLimiter(Clock clock, int limit) {
        this.clock = clock;
        this.limit = limit;
    }

    public synchronized boolean tryAcquire(String key) {
        long window = clock.millis() / WINDOW_MILLIS;
        if (window != currentWindow) {
            counts.clear();
            currentWindow = window;
        }
        return counts.merge(key, 1, Integer::sum) <= limit;
    }
}
