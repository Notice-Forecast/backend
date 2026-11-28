package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeriesAdminServiceTest {

    private static final List<TermCalendar> TERMS = List.of(
            TermCalendar.of("2026-1", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
            TermCalendar.of("2026-2", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 12, 20)));

    private static NoticeTitleRow row(String postedUtc) {
        return new NoticeTitleRow(1L, 11L, "복수전공 변경 안내", "컴퓨터정보공학부", null, Instant.parse(postedUtc));
    }

    @Test
    void 게시일이_속한_학기와_학기_시작_끝에서_며칠인지_계산한다() {
        SeriesAdminService.Round round = SeriesAdminService.round(row("2026-11-22T15:00:00Z"), TERMS); // 11/23 KST

        assertThat(round.term()).isEqualTo("2026-2");
        assertThat(round.posted()).isEqualTo(LocalDate.of(2026, 11, 23));
        assertThat(round.daysFromTermStart()).isEqualTo(84);
        assertThat(round.daysFromTermEnd()).isEqualTo(-27);
    }

    @Test
    void 학사일정이_없는_해는_학기를_비운다() {
        SeriesAdminService.Round round = SeriesAdminService.round(row("2019-11-18T15:00:00Z"), TERMS);

        assertThat(round.term()).isNull();
        assertThat(round.daysFromTermStart()).isNull();
    }
}
