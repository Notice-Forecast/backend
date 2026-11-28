package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Test
    void 계열_판별이_돌고_있으면_합치기를_409로_거절한다() {
        SeriesLinker linker = mock(SeriesLinker.class);
        when(linker.tryLock()).thenReturn(false);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        SeriesAdminService service = new SeriesAdminService(mock(ForecastSeriesRepository.class),
                mock(NoticeRepository.class), mock(TermCalendarRepository.class), linker,
                new TransactionTemplate(txManager));

        assertThatThrownBy(() -> service.merge(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERIES_LINKING_IN_PROGRESS));
    }

    @Test
    void curate는_학사일정을_읽지_않고_요약만_돌려준다() {
        ForecastSeriesRepository seriesRepo = mock(ForecastSeriesRepository.class);
        NoticeRepository noticeRepo = mock(NoticeRepository.class);
        TermCalendarRepository termRepo = mock(TermCalendarRepository.class);
        ForecastSeries series = ForecastSeries.startFrom("근로장학생", "근로장학생 모집", "학생지원팀", null,
                Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(series, "id", 5L);
        when(seriesRepo.findById(5L)).thenReturn(Optional.of(series));
        when(noticeRepo.summarizeSeries(5L)).thenReturn(new NoticeRepository.SeriesStats(
                3L, Instant.parse("2025-03-01T00:00:00Z"), Instant.parse("2026-03-01T00:00:00Z")));
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        SeriesAdminService service = new SeriesAdminService(seriesRepo, noticeRepo, termRepo,
                mock(SeriesLinker.class), new TransactionTemplate(txManager));

        SeriesAdminService.SeriesSummary summary = service.curate(5L, "교내 근로장학생", SeriesAnchor.TERM_START, true);

        assertThat(summary.name()).isEqualTo("교내 근로장학생");
        assertThat(summary.rounds()).isEqualTo(3L);
        assertThat(summary.seed()).isTrue();
        verify(termRepo, never()).findAll();
    }
}
