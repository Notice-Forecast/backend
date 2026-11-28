package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.extract.LlmProperties;
import com.nf.noticeforecast.extract.LlmRejectedException;
import com.nf.noticeforecast.extract.LlmUnavailableException;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 계열이 없는 공지를 오래된 글부터 계열에 붙인다(CLAUDE.md 8.4, 11/28 결정).
 * <ol>
 *   <li>정규화 제목 유사도로 후보를 고른다. 같은 부서는 50% 이상, 다른 부서(재게시)는 80% 이상. 계열마다 가장 비슷한 1건, 최대 3개.</li>
 *   <li>후보가 있으면 LLM이 후보마다 같은 공지의 다른 회차인지 판별하고, 그 결과와 이유를 series_judgment에 남긴다.</li>
 *   <li>같다고 한 후보 중 가장 비슷한 계열에 붙인다. 후보가 없거나 모두 다르면 이 공지를 첫 회차로 새 계열을 만든다.</li>
 * </ol>
 * 과거 이력까지 한 번에 돌리면 오래 걸려서 내부 API는 백그라운드로 실행한다. 정기 수집 뒤에도 호출해 새 공지를 붙인다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeriesLinker {

    static final int MAX_CANDIDATES = 3;

    private final NoticeRepository noticeRepository;
    private final ForecastSeriesRepository seriesRepository;
    private final SeriesJudgmentRepository judgmentRepository;
    private final SeriesJudge judge;
    private final LlmProperties llmProperties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final ExecutorService background = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "series-linker");
        thread.setDaemon(true);
        return thread;
    });

    public record LinkReport(boolean ran, int linked, int created, int joined, int llmCalls, int failed,
                             String stoppedBy) {

        static LinkReport notRun(String reason) {
            return new LinkReport(false, 0, 0, 0, 0, 0, reason);
        }
    }

    record Entry(NoticeTitleRow row, long seriesId, String departmentKey, Set<String> bigrams) {
    }

    record Candidate(Entry entry, double similarity) {
    }

    /** @return 새로 시작했으면 true, 이미 돌고 있으면 false */
    public boolean startInBackground() {
        if (lock.isLocked()) {
            return false;
        }
        background.submit(this::linkPending);
        return true;
    }

    public boolean running() {
        return lock.isLocked();
    }

    public LinkReport linkPending() {
        if (!lock.tryLock()) {
            return LinkReport.notRun("이미 실행 중입니다.");
        }
        try {
            if (!llmProperties.configured()) {
                log.warn("series linking skipped: LLM api key is not configured");
                return LinkReport.notRun("LLM API 키가 없습니다.");
            }
            return run();
        } finally {
            lock.unlock();
        }
    }

    private LinkReport run() {
        List<Entry> index = new ArrayList<>();
        List<NoticeTitleRow> pending = new ArrayList<>();
        for (NoticeTitleRow row : noticeRepository.findAllTitles()) {
            if (row.seriesId() == null) {
                pending.add(row);
            } else {
                index.add(entry(row, row.seriesId()));
            }
        }
        int created = 0;
        int joined = 0;
        int calls = 0;
        int failed = 0;
        String stoppedBy = null;
        for (NoticeTitleRow row : pending) {
            List<Candidate> candidates = candidates(row, index);
            Long seriesId = null;
            if (!candidates.isEmpty()) {
                try {
                    calls++;
                    seriesId = judgeAndRecord(row, candidates);
                } catch (LlmUnavailableException | LlmRejectedException e) {
                    stoppedBy = e.getMessage();
                    log.warn("series linking stopped at notice {}: {}", row.id(), e.getMessage());
                    break;
                } catch (RuntimeException e) {
                    // 응답을 읽지 못한 공지를 남겨 두면 매번 같은 호출을 다시 하므로, 모두 다르다고 본 것처럼 새 계열을 만든다.
                    failed++;
                    log.warn("series judgment unreadable for notice {}, starting a new series: {}", row.id(), e.toString());
                    recordUnreadable(row, candidates, e);
                }
            }
            if (seriesId == null) {
                seriesId = seriesRepository.save(ForecastSeries.startFrom(SeriesTitles.normalize(row.title()),
                        row.title(), row.department(), row.category(), clock.instant())).getId();
                created++;
            } else {
                joined++;
            }
            noticeRepository.assignSeries(row.id(), seriesId);
            index.add(entry(row, seriesId));
        }
        LinkReport report = new LinkReport(true, created + joined, created, joined, calls, failed, stoppedBy);
        log.info("series linking finished: {}", report);
        return report;
    }

    /** @return 같다고 판별된 계열. 모두 다르면 null */
    private Long judgeAndRecord(NoticeTitleRow row, List<Candidate> candidates) {
        SeriesJudge.Result result = judge.judge(row, candidates.stream().map(c -> c.entry().row()).toList());
        Map<Integer, SeriesJudge.Verdict> byCandidate = new HashMap<>();
        result.verdicts().forEach(v -> byCandidate.putIfAbsent(v.candidate(), v));
        Instant now = clock.instant();
        List<SeriesJudgment> records = new ArrayList<>();
        Long matched = null;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            SeriesJudge.Verdict verdict = byCandidate.get(i + 1);
            boolean same = verdict != null && verdict.same();
            records.add(SeriesJudgment.of(row.id(), candidate.entry().row().id(), candidate.entry().seriesId(),
                    candidate.similarity(), same, verdict == null ? "응답에 판단이 없었습니다." : verdict.reason(),
                    result.model(), now));
            if (same && matched == null) {
                matched = candidate.entry().seriesId();
            }
        }
        judgmentRepository.saveAll(records);
        return matched;
    }

    private void recordUnreadable(NoticeTitleRow row, List<Candidate> candidates, RuntimeException e) {
        Instant now = clock.instant();
        String reason = "LLM 응답을 읽지 못해 새 계열로 처리했습니다: " + e.getMessage();
        judgmentRepository.saveAll(candidates.stream()
                .map(c -> SeriesJudgment.of(row.id(), c.entry().row().id(), c.entry().seriesId(), c.similarity(),
                        false, reason, null, now))
                .toList());
    }

    static Entry entry(NoticeTitleRow row, long seriesId) {
        return new Entry(row, seriesId, SeriesTitles.departmentKey(row.department()),
                SeriesTitles.bigrams(SeriesTitles.normalize(row.title())));
    }

    /** 이 공지보다 먼저(같은 시각 포함) 올라온, 이미 계열이 있는 공지 중 후보. 계열마다 가장 비슷한 1건, 비슷한 순 최대 3개. */
    static List<Candidate> candidates(NoticeTitleRow row, List<Entry> index) {
        String normalized = SeriesTitles.normalize(row.title());
        if (SeriesTitles.tooShort(normalized)) {
            return List.of();
        }
        Set<String> grams = SeriesTitles.bigrams(normalized);
        String departmentKey = SeriesTitles.departmentKey(row.department());
        Map<Long, Candidate> bestPerSeries = new HashMap<>();
        for (Entry entry : index) {
            if (entry.row().postedAt().isAfter(row.postedAt())) {
                continue;
            }
            double similarity = SeriesTitles.similarity(grams, entry.bigrams());
            boolean sameDepartment = !departmentKey.isEmpty() && departmentKey.equals(entry.departmentKey());
            double threshold = sameDepartment ? SeriesTitles.SAME_DEPARTMENT_THRESHOLD
                    : SeriesTitles.OTHER_DEPARTMENT_THRESHOLD;
            if (similarity < threshold) {
                continue;
            }
            Candidate current = bestPerSeries.get(entry.seriesId());
            if (current == null || similarity > current.similarity()) {
                bestPerSeries.put(entry.seriesId(), new Candidate(entry, similarity));
            }
        }
        return bestPerSeries.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::similarity).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }
}
