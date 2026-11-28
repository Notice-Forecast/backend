package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ForecastNoticeRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForecastCalculatorTest {

    private static final List<TermCalendar> TERMS = List.of(
            TermCalendar.of("2021-1", LocalDate.of(2021, 3, 2), LocalDate.of(2021, 6, 20)),
            TermCalendar.of("2024-1", LocalDate.of(2024, 3, 4), LocalDate.of(2024, 6, 23)),
            TermCalendar.of("2024-2", LocalDate.of(2024, 9, 2), LocalDate.of(2024, 12, 22)),
            TermCalendar.of("2025-1", LocalDate.of(2025, 3, 4), LocalDate.of(2025, 6, 22)),
            TermCalendar.of("2025-2", LocalDate.of(2025, 9, 1), LocalDate.of(2025, 12, 21)),
            TermCalendar.of("2026-1", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
            TermCalendar.of("2026-2", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 12, 20)),
            TermCalendar.of("2027-1", LocalDate.of(2027, 3, 2), LocalDate.of(2027, 6, 21)));

    private static ForecastNoticeRow notice(long id, LocalDate kstDate) {
        return new ForecastNoticeRow(id, 1L, 1L, "공지 " + id,
                kstDate.atStartOfDay(KoreanTime.ZONE).toInstant(), null);
    }

    @Test
    void 다음_학기_시작_45일_전부터는_다음_학기_회차로_본다() {
        // 2026-1 시작은 2026-03-03 -> 45일 전은 2026-01-17
        assertThat(ForecastCalculator.assignTerm(LocalDate.of(2026, 2, 20), TERMS).getTerm()).isEqualTo("2026-1");
        // 6월 초는 2026-2 시작(8/31) 45일 전보다 앞이라 2026-1
        assertThat(ForecastCalculator.assignTerm(LocalDate.of(2026, 6, 5), TERMS).getTerm()).isEqualTo("2026-1");
        // 2019·2020년은 학사일정이 없어 null
        assertThat(ForecastCalculator.assignTerm(LocalDate.of(2019, 11, 18), TERMS)).isNull();
    }

    @Test
    void 학기마다_첫_게시만_회차로_세고_다음_학기_기간과_HIGH_신뢰도를_계산한다() {
        List<ForecastNoticeRow> rows = List.of(
                notice(1, LocalDate.of(2025, 5, 25)), // 2025-1 end(6/22) -28
                notice(2, LocalDate.of(2025, 5, 29)), // 같은 학기 재게시 -> 제외
                notice(3, LocalDate.of(2025, 11, 23)), // 2025-2 end(12/21) -28
                notice(4, LocalDate.of(2026, 5, 25)), // 2026-1 end(6/22) -28
                notice(5, LocalDate.of(2026, 11, 22))); // 2026-2 end(12/20) -28

        ForecastCalculator.Prediction prediction = ForecastCalculator.predict(
                SeriesAnchor.TERM_END, rows, TERMS, LocalDate.of(2026, 11, 29));

        // 2027-1 end(2027-06-21) - 28일 = 2027-05-24, stdDev=0 -> halfWidth=2
        assertThat(prediction.expectedFrom()).isEqualTo(LocalDate.of(2027, 5, 22));
        assertThat(prediction.expectedTo()).isEqualTo(LocalDate.of(2027, 5, 26));
        assertThat(prediction.confidence()).isEqualTo(ForecastConfidence.HIGH);
        assertThat(prediction.past()).hasSize(4);
        assertThat(prediction.past().getFirst().term()).isEqualTo("2026-2");
        assertThat(prediction.reason()).contains("4개 학기").contains("학기 종료").contains("4주 전");
    }

    @Test
    void 회차가_2회_이하면_LOW이고_편차가_7일을_넘으면_MEDIUM에_반폭은_7일로_제한된다() {
        List<ForecastNoticeRow> twoRounds = List.of(
                notice(1, LocalDate.of(2026, 2, 20)),
                notice(2, LocalDate.of(2026, 8, 20)));
        assertThat(ForecastCalculator.predict(SeriesAnchor.TERM_START, twoRounds, TERMS, LocalDate.of(2026, 11, 29))
                .confidence()).isEqualTo(ForecastConfidence.LOW);

        List<ForecastNoticeRow> noisy = List.of(
                notice(1, LocalDate.of(2025, 3, 4)),  // 0
                notice(2, LocalDate.of(2025, 9, 21)), // +20
                notice(3, LocalDate.of(2026, 2, 11)), // -20
                notice(4, LocalDate.of(2026, 9, 20))); // +20
        ForecastCalculator.Prediction pred = ForecastCalculator.predict(
                SeriesAnchor.TERM_START, noisy, TERMS, LocalDate.of(2026, 11, 29));

        assertThat(pred.confidence()).isEqualTo(ForecastConfidence.MEDIUM);
        assertThat(pred.expectedTo().toEpochDay() - pred.expectedFrom().toEpochDay()).isEqualTo(14);
    }

    @Test
    void 비대면_2021학년도_회차는_가중치_0점5를_곱한다() {
        List<ForecastNoticeRow> rows = List.of(
                notice(1, LocalDate.of(2021, 3, 22)), // 2021-1 start +20, w = 0.8 * 0.5 = 0.4
                notice(2, LocalDate.of(2026, 3, 3))); // 2026-1 start 0, w = 1.0

        ForecastCalculator.Prediction pred = ForecastCalculator.predict(
                SeriesAnchor.TERM_START, rows, TERMS, LocalDate.of(2026, 11, 29));

        // weighted mean = (0.4 * 20 + 1.0 * 0) / 1.4 = 5.714...
        assertThat(pred.meanOffsetDays()).isBetween(5.7, 5.72);
    }

    @Test
    void 다음_학기_학사일정이_없으면_작년_같은_학기_날짜에_1년을_더해_예측한다() {
        List<TermCalendar> until2026 = TERMS.stream().filter(t -> !t.getTerm().startsWith("2027-")).toList();
        List<ForecastNoticeRow> rows = List.of(
                notice(1, LocalDate.of(2026, 2, 21)), // 2026-1 start(3/3) -10
                notice(2, LocalDate.of(2026, 8, 21))); // 2026-2 start(8/31) -10

        ForecastCalculator.Prediction pred = ForecastCalculator.predict(
                SeriesAnchor.TERM_START, rows, until2026, LocalDate.of(2026, 11, 29));

        // 2027-1 추정 시작일 = 2026-03-03 + 1년 = 2027-03-03, -10일 = 2027-02-21, ±2일
        assertThat(pred.expectedFrom()).isEqualTo(LocalDate.of(2027, 2, 19));
        assertThat(pred.expectedTo()).isEqualTo(LocalDate.of(2027, 2, 23));
    }
}
