package com.nf.noticeforecast.crawl;

import java.time.Duration;

public class RequestThrottle {

    private final long intervalMillis;
    private long lastRequestAt;

    public RequestThrottle(Duration interval) {
        this.intervalMillis = interval.toMillis();
    }

    public synchronized void await() throws InterruptedException {
        long waitMillis = lastRequestAt + intervalMillis - System.currentTimeMillis();
        if (waitMillis > 0) {
            Thread.sleep(waitMillis);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
