package com.nf.noticeforecast.global.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock, 2);

    @Test
    void 한도를_넘으면_막고_키마다_따로_센다() {
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void 다음_분이_되면_다시_허용한다() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("a");
        clock.now = clock.now.plusSeconds(60);
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-26T00:00:00Z");

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
            return now;
        }
    }
}
