package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TitleDeadlineParserTest {

    private final TitleDeadlineParser parser = new TitleDeadlineParser();

    @Test
    void 연도와_시각이_모두_있으면_기간의_끝을_마감으로_본다() {
        assertThat(parser.parse("국가장학금 1차 신청 안내(2026. 11. 24.(화) 9시 ~ 2027. 1. 15.(금) 18시)"))
                .contains(Instant.parse("2027-01-15T09:00:00Z"));
    }

    @Test
    void 분까지_읽는다() {
        assertThat(parser.parse("근로장학생 신청 (~ 2026. 12. 3.(목) 17:30까지)"))
                .contains(Instant.parse("2026-12-03T08:30:00Z"));
    }

    @Test
    void 연도가_없으면_비운다() {
        assertThat(parser.parse("총학생회장단 선거 (~11월25일 18:30까지)")).isEmpty();
        assertThat(parser.parse("관정재단 장학생 선발 안내( ~ 11. 23.(월) 18시까지)")).isEmpty();
    }

    @Test
    void 시각이_없으면_비운다() {
        assertThat(parser.parse("장학생 선발 안내 (~ 2026. 11. 23.(월)까지)")).isEmpty();
    }

    @Test
    void 마감_표기가_없으면_비운다() {
        assertThat(parser.parse("2027학년도 1학기 프란치스코관 모집공고")).isEmpty();
    }
}
