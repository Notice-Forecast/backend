package com.nf.noticeforecast.global.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class KoreanTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreanTime() {
    }

    public static OffsetDateTime from(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.SECONDS).atZone(ZONE).toOffsetDateTime();
    }
}
