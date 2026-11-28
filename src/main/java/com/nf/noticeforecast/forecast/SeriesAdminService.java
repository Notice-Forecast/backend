package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시드 계열을 고르기 위한 내부 관리(CLAUDE.md 8.4, 11/28 결정). 회차 많은 계열을 보고, 잘못 나뉜 계열을 합치고,
 * 고른 계열에 이름과 기준점(TERM_START/TERM_END)을 붙인다.
 */
@Service
@RequiredArgsConstructor
public class SeriesAdminService {

    private final ForecastSeriesRepository seriesRepository;
    private final NoticeRepository noticeRepository;
    private final TermCalendarRepository termCalendarRepository;
    private final SeriesLinker seriesLinker;
    private final TransactionTemplate transactionTemplate;

    public record SeriesSummary(Long id, String name, String department, boolean seed, SeriesAnchor anchor,
                                long rounds, LocalDate firstPosted, LocalDate lastPosted) {
    }

    /** @param term 게시일이 속한 학기(학기 시작일 기준). 학사일정이 없는 해(2019·2020)는 null */
    public record Round(Long noticeId, LocalDate posted, String title, String department, String term,
                        Long daysFromTermStart, Long daysFromTermEnd) {
    }

    public record SeriesDetail(SeriesSummary series, List<Round> rounds) {
    }

    @Transactional(readOnly = true)
    public List<SeriesSummary> list(long minRounds) {
        List<Object[]> counts = noticeRepository.countRoundsBySeries(minRounds);
        Map<Long, ForecastSeries> series = seriesRepository.findAllById(
                        counts.stream().map(row -> (Long) row[0]).toList()).stream()
                .collect(Collectors.toMap(ForecastSeries::getId, Function.identity()));
        return counts.stream()
                .filter(row -> series.containsKey((Long) row[0]))
                .map(row -> summary(series.get((Long) row[0]), (Long) row[1], (Instant) row[2], (Instant) row[3]))
                .sorted(Comparator.comparingLong(SeriesSummary::rounds).reversed().thenComparing(SeriesSummary::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public SeriesDetail detail(Long id) {
        ForecastSeries series = find(id);
        List<NoticeTitleRow> notices = noticeRepository.findTitlesBySeries(id);
        List<TermCalendar> terms = termCalendarRepository.findAll().stream()
                .sorted(Comparator.comparing(TermCalendar::getStartDate))
                .toList();
        List<Round> rounds = notices.stream().map(row -> round(row, terms)).toList();
        SeriesSummary summary = summary(series, notices.size(),
                notices.isEmpty() ? null : notices.getFirst().postedAt(),
                notices.isEmpty() ? null : notices.getLast().postedAt());
        return new SeriesDetail(summary, rounds);
    }

    /** from 계열의 공지를 모두 into 계열로 옮기고 from 계열을 지운다. 판별 기록은 그대로 둔다. */
    public int merge(Long intoId, Long fromId) {
        if (intoId.equals(fromId)) {
            throw new InvalidFieldException("from", "같은 계열끼리는 합칠 수 없습니다.");
        }
        if (!seriesLinker.tryLock()) {
            throw new BusinessException(ErrorCode.SERIES_LINKING_IN_PROGRESS);
        }
        try {
            Integer moved = transactionTemplate.execute(status -> {
                find(intoId);
                ForecastSeries from = find(fromId);
                int count = noticeRepository.moveSeries(fromId, intoId);
                seriesRepository.delete(from);
                return count;
            });
            return moved == null ? 0 : moved;
        } finally {
            seriesLinker.unlock();
        }
    }

    @Transactional
    public SeriesSummary curate(Long id, String name, SeriesAnchor anchor, boolean seed) {
        ForecastSeries series = find(id);
        series.curate(name, anchor, seed);
        NoticeRepository.SeriesStats stats = noticeRepository.summarizeSeries(id);
        return summary(series, stats == null ? 0 : stats.rounds(),
                stats == null ? null : stats.firstPostedAt(),
                stats == null ? null : stats.lastPostedAt());
    }

    static Round round(NoticeTitleRow row, List<TermCalendar> terms) {
        LocalDate posted = LocalDate.ofInstant(row.postedAt(), KoreanTime.ZONE);
        TermCalendar term = null;
        for (TermCalendar candidate : terms) {
            if (!candidate.getStartDate().isAfter(posted)) {
                term = candidate;
            }
        }
        if (term == null) {
            return new Round(row.id(), posted, row.title(), row.department(), null, null, null);
        }
        Long fromEnd = term.getEndDate() == null ? null : ChronoUnit.DAYS.between(term.getEndDate(), posted);
        return new Round(row.id(), posted, row.title(), row.department(), term.getTerm(),
                ChronoUnit.DAYS.between(term.getStartDate(), posted), fromEnd);
    }

    private ForecastSeries find(Long id) {
        return seriesRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.SERIES_NOT_FOUND));
    }

    private static SeriesSummary summary(ForecastSeries series, long rounds, Instant first, Instant last) {
        return new SeriesSummary(series.getId(), series.getName(), series.getDepartment(), series.isSeed(),
                series.getAnchor(), rounds,
                first == null ? null : LocalDate.ofInstant(first, KoreanTime.ZONE),
                last == null ? null : LocalDate.ofInstant(last, KoreanTime.ZONE));
    }
}
