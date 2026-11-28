package com.nf.noticeforecast.metrics;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.EnrollmentStatus;
import com.nf.noticeforecast.extract.LlmProperties;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.notice.DepartmentNames;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeCardRow;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.push.NoticeAlerts;
import com.nf.noticeforecast.push.ServerEligibility;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정확도 실측(CLAUDE.md 15.2). jar에 실은 정답셋을 배포 DB의 카드와 비교해 결과를 저장하고, GET /metrics는 마지막 결과를 준다.
 * 알림량은 최근 30일 새 공지(카드 있는 것) 전체 대비, 페르소나별로 서버 판정(matchFilter)과 학과 공지 노출 규칙으로 새 공지 알림을 받을
 * 공지 수다(11/28 결정). 예보 적중률은 백테스트를 구현한 뒤 채운다.
 */
@Service
@RequiredArgsConstructor
public class MetricsService {

    static final Duration ALERT_WINDOW = Duration.ofDays(30);

    private final EvalDataset dataset;
    private final NoticeRepository noticeRepository;
    private final NoticeSourceRepository sourceRepository;
    private final MetricsRunRepository runRepository;
    private final MetricsReviewRepository reviewRepository;
    private final LlmProperties llmProperties;
    private final Clock clock;

    /** 트랜잭션으로 감싸지 않아 synchronized가 저장까지 덮는다. 읽기와 저장은 저장소 메서드마다 따로 커밋된다. */
    public synchronized MetricsResponse run() {
        Instant now = clock.instant();
        List<EvalLabel> labels = dataset.labels();
        Map<String, Persona> personas = dataset.personas();
        List<NoticeSource> sources = sourceRepository.findAll();
        Map<String, Long> sourceIds = sourceIdsByName(sources);
        List<MetricsCalculator.Sample> samples = labels.stream()
                .map(label -> sample(label, findNotice(label, sourceIds)))
                .toList();
        Map<Long, MetricsCalculator.Review> reviews = reviewRepository.findAll().stream()
                .collect(Collectors.toMap(MetricsReview::getArticleNo, r -> new MetricsCalculator.Review(
                        r.getTargetCorrect(), r.getDocumentsMatched(), r.getNoticeRevision())));
        MetricsReport report = MetricsCalculator.compute(llmProperties.model(), samples, personas, reviews,
                alerts(personas, sources, now));
        return MetricsResponse.of(runRepository.save(MetricsRun.of(report, now)));
    }

    @Transactional(readOnly = true)
    public MetricsResponse latest() {
        return runRepository.findFirstByOrderByComputedAtDescIdDesc()
                .map(MetricsResponse::of)
                .orElseGet(MetricsResponse::empty);
    }

    /** 사람이 표시한 대상·서류 결과를 저장한다. 표시할 때의 카드 revision을 함께 둔다. */
    @Transactional
    public void review(Long articleNo, Boolean targetCorrect, Integer documentsMatched) {
        EvalLabel label = dataset.labels().stream()
                .filter(l -> l.articleNo().equals(articleNo))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        if (documentsMatched != null) {
            if (!label.full()) {
                throw new InvalidFieldException("documentsMatched", "BASIC 공지는 서류를 재지 않습니다.");
            }
            if (documentsMatched > label.documents().size()) {
                throw new InvalidFieldException("documentsMatched",
                        "정답 서류 수(" + label.documents().size() + "개)보다 클 수 없습니다.");
            }
        }
        Notice notice = findNotice(label, sourceIdsByName(sourceRepository.findAll()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        reviewRepository.save(MetricsReview.of(articleNo, targetCorrect, documentsMatched, notice.getRevision(),
                clock.instant()));
    }

    List<MetricsReport.AlertVolume> alerts(Map<String, Persona> personas, List<NoticeSource> sources, Instant now) {
        List<NoticeCardRow> notices = noticeRepository.findCardRowsPostedSince(now.minus(ALERT_WINDOW));
        Map<Long, NoticeSource> sourcesById = sources.stream()
                .collect(Collectors.toMap(NoticeSource::getId, Function.identity()));
        List<MetricsReport.AlertVolume> volumes = new ArrayList<>();
        for (Map.Entry<String, Persona> entry : new TreeMap<>(personas).entrySet()) {
            Device device = asDevice(entry.getValue(), now);
            int notified = (int) notices.stream()
                    .filter(row -> ServerEligibility.notExcluded(row.card(), device)
                            && NoticeAlerts.seesSource(device, sourcesById.get(row.sourceId())))
                    .count();
            int total = notices.size();
            Double reduction = total == 0 ? null : MetricsReport.round(total - notified, total);
            volumes.add(new MetricsReport.AlertVolume(entry.getKey(), notified, total, reduction));
        }
        return volumes;
    }

    /** 서버 판정은 기기의 matchFilter(주전공·추가 전공·학년·학적)만 본다. 저장하지 않는 임시 기기다. */
    static Device asDevice(Persona persona, Instant now) {
        Device device = Device.register("EVAL-0000", now);
        device.updateMatchFilter(persona.department(), persona.extraDepartments(),
                persona.grade() == null ? 0 : persona.grade(), EnrollmentStatus.valueOf(persona.enrollmentStatus()));
        return device;
    }

    private Optional<Notice> findNotice(EvalLabel label, Map<String, Long> sourceIds) {
        Long sourceId = sourceIds.get(DepartmentNames.normalize(label.source()));
        return sourceId == null ? Optional.empty()
                : noticeRepository.findBySourceIdAndArticleNo(sourceId, label.articleNo());
    }

    private static MetricsCalculator.Sample sample(EvalLabel label, Optional<Notice> found) {
        if (found.isEmpty()) {
            return new MetricsCalculator.Sample(label, null, null, null, false, 0);
        }
        Notice notice = found.get();
        return new MetricsCalculator.Sample(label, notice.getExtractionStatus(), notice.getCard(),
                notice.getDeadlineAt(), notice.isDeadlineAllDay(), notice.getRevision());
    }

    private static Map<String, Long> sourceIdsByName(List<NoticeSource> sources) {
        Map<String, Long> ids = new HashMap<>();
        sources.forEach(source -> ids.putIfAbsent(DepartmentNames.normalize(source.getName()), source.getId()));
        return ids;
    }
}
