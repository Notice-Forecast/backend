package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.extract.LlmProperties;
import com.nf.noticeforecast.extract.LlmUnavailableException;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SeriesLinkerTest {

    private static final Instant T = Instant.parse("2025-03-01T00:00:00Z");

    private final NoticeRepository notices = mock(NoticeRepository.class);
    private final ForecastSeriesRepository seriesRepository = mock(ForecastSeriesRepository.class);
    private final SeriesJudgmentRepository judgments = mock(SeriesJudgmentRepository.class);
    private final SeriesJudge judge = mock(SeriesJudge.class);
    private final AtomicLong nextSeriesId = new AtomicLong(100);
    private SeriesLinker linker;

    private static NoticeTitleRow row(long id, Long seriesId, String title, String department, int daysAfter) {
        return new NoticeTitleRow(id, seriesId, title, department, null, T.plusSeconds(86_400L * daysAfter));
    }

    @BeforeEach
    void setUp() {
        linker = new SeriesLinker(notices, seriesRepository, judgments, judge,
                new LlmProperties("key", null, null, null), Clock.fixed(T, ZoneOffset.UTC));
        when(seriesRepository.save(any())).thenAnswer(invocation -> {
            ForecastSeries series = invocation.getArgument(0);
            ReflectionTestUtils.setField(series, "id", nextSeriesId.getAndIncrement());
            return series;
        });
    }

    @Test
    void 후보는_같은_부서_50퍼센트_다른_부서_80퍼센트_이상이고_나중_글은_빼고_계열마다_하나씩_최대_3개다() {
        NoticeTitleRow notice = row(10, null, "2026-1학기 장학조교(근로 B) 모집", "학생지원팀", 400);
        List<SeriesLinker.Entry> index = List.of(
                SeriesLinker.entry(row(1, 1L, "25-1학기 장학조교(근로 B) 모집", "학생지원팀", 0), 1L),
                SeriesLinker.entry(row(2, 1L, "25-2학기 장학조교(근로 B) 모집", "학생지원팀", 180), 1L),
                SeriesLinker.entry(row(3, 2L, "장학조교 모집", "컴퓨터정보공학부", 10), 2L),
                SeriesLinker.entry(row(4, 3L, "수업장학조교 모집 안내", "컴퓨터정보공학부", 10), 3L),
                SeriesLinker.entry(row(5, 4L, "2027-1학기 장학조교 모집", "학생지원팀", 800), 4L));

        List<SeriesLinker.Candidate> candidates = SeriesLinker.candidates(notice, index);

        assertThat(candidates).extracting(c -> c.entry().seriesId()).containsExactly(1L, 2L);
    }

    @Test
    void 같다고_판별되면_그_계열에_붙고_후보가_없으면_새_계열을_만든다() {
        when(notices.findAllTitles()).thenReturn(List.of(
                row(1, null, "25-1학기 장학조교(근로 B) 모집", "학생지원팀", 0),
                row(2, null, "25-2학기 장학조교(근로 B) 모집", "학생지원팀", 180),
                row(3, null, "학생 상담 프로그램 안내", "학생지원팀", 190)));
        when(judge.judge(any(), anyList())).thenReturn(new SeriesJudge.Result(
                List.of(new SeriesJudge.Verdict(1, true, "같은 장학조교 모집의 다음 학기 회차")), "claude-haiku-5-5"));

        SeriesLinker.LinkReport report = linker.linkPending();

        assertThat(report.created()).isEqualTo(2);
        assertThat(report.joined()).isEqualTo(1);
        assertThat(report.llmCalls()).isEqualTo(1);
        verify(notices).assignSeries(1L, 100L);
        verify(notices).assignSeries(2L, 100L);
        verify(notices).assignSeries(3L, 101L);
        verify(judgments).saveAll(anyList());
    }

    @Test
    void 모두_다르다고_하면_새_계열을_만든다() {
        when(notices.findAllTitles()).thenReturn(List.of(
                row(1, 7L, "2025 동계방학 기숙사 모집 공고", "기숙사운영팀", 0),
                row(2, null, "2025 하계방학 기숙사 모집 공고", "기숙사운영팀", 120)));
        when(judge.judge(any(), anyList())).thenReturn(new SeriesJudge.Result(
                List.of(new SeriesJudge.Verdict(1, false, "계절이 다름")), "claude-haiku-5-5"));

        linker.linkPending();

        verify(notices).assignSeries(2L, 100L);
    }

    @Test
    void LLM을_쓸_수_없으면_멈추고_남은_공지는_다음에_다시_한다() {
        when(notices.findAllTitles()).thenReturn(List.of(
                row(1, 7L, "25-1학기 장학조교 모집", "학생지원팀", 0),
                row(2, null, "25-2학기 장학조교 모집", "학생지원팀", 180)));
        when(judge.judge(any(), anyList())).thenThrow(new LlmUnavailableException("429", null));

        SeriesLinker.LinkReport report = linker.linkPending();

        assertThat(report.stoppedBy()).isEqualTo("429");
        verify(notices, never()).assignSeries(eq(2L), any());
    }

    @Test
    void 응답을_읽지_못하면_새_계열을_만들고_기록을_남겨_다시_호출하지_않는다() {
        when(notices.findAllTitles()).thenReturn(List.of(
                row(1, 7L, "25-1학기 장학조교 모집", "학생지원팀", 0),
                row(2, null, "25-2학기 장학조교 모집", "학생지원팀", 180)));
        when(judge.judge(any(), anyList())).thenThrow(new IllegalStateException("JSON을 읽을 수 없습니다"));

        SeriesLinker.LinkReport report = linker.linkPending();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.created()).isEqualTo(1);
        verify(notices).assignSeries(2L, 100L);
        verify(judgments).saveAll(anyList());
    }

    @Test
    void LLM_키가_없으면_돌지_않는다() {
        SeriesLinker noKey = new SeriesLinker(notices, seriesRepository, judgments, judge,
                new LlmProperties("", null, null, null), Clock.systemUTC());

        assertThat(noKey.linkPending().ran()).isFalse();
        verify(notices, never()).findAllTitles();
    }
}
