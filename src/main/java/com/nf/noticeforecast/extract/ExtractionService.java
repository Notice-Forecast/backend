package com.nf.noticeforecast.extract;

import com.nf.noticeforecast.attachment.AttachmentCollector;
import com.nf.noticeforecast.attachment.AttachmentText;
import com.nf.noticeforecast.crawl.RobotsPolicy;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.CardDiff;
import com.nf.noticeforecast.notice.ExtractionReason;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeChange;
import com.nf.noticeforecast.notice.NoticeChangeRepository;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.push.NoticeAlerts;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private static final Duration RECENT = Duration.ofDays(30);
    private static final int LOCK_STRIPES = 64;
    /** 공지 하나의 400은 건너뛰되, 연속으로 이만큼 거절되면 우리 요청 문제로 보고 배치를 멈춘다. */
    static final int MAX_CONSECUTIVE_REJECTED = 3;

    private final NoticeRepository noticeRepository;
    private final NoticeChangeRepository noticeChangeRepository;
    private final NoticeAlerts noticeAlerts;
    private final CardExtractor extractor;
    private final AttachmentCollector attachmentCollector;
    private final LlmProperties llmProperties;
    private final TransactionTemplate transactionTemplate;

    private final AtomicBoolean batchRunning = new AtomicBoolean(false);
    private final ReentrantLock[] noticeLocks = createLocks();

    public record BatchReport(int extracted, int needsOriginal, int retryLater) {
    }

    private record SavedCard(Notice notice, List<CardDiff.CardChange> changes) {
    }

    private static ReentrantLock[] createLocks() {
        ReentrantLock[] locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantLock();
        }
        return locks;
    }

    private ReentrantLock lockFor(Long noticeId) {
        return noticeLocks[Math.floorMod(Long.hashCode(noticeId), LOCK_STRIPES)];
    }

    /** 최근 30일 글 중 아직 추출하지 않은 공지를 카드로 바꾼다. 호출이 실패한 공지는 PENDING으로 남아 다음 배치에서 다시 시도한다. */
    public BatchReport extractPending() {
        if (!llmProperties.configured()) {
            log.warn("LLM api key is not set; skip extraction");
            return new BatchReport(0, 0, 0);
        }
        if (!batchRunning.compareAndSet(false, true)) {
            log.info("extraction batch already running; skip");
            return new BatchReport(0, 0, 0);
        }
        int extracted = 0;
        int needsOriginal = 0;
        int retryLater = 0;
        int consecutiveRejected = 0;
        try {
            RobotsPolicy robots = attachmentCollector.newRobotsPolicy();
            for (Long noticeId : noticeRepository.findExtractionCandidateIds(Instant.now().minus(RECENT))) {
                CardExtractor.Outcome outcome;
                try {
                    outcome = extractOne(noticeId, false, robots);
                } catch (RuntimeException e) {
                    log.warn("extraction failed for notice {}: {}", noticeId, e.toString());
                    continue;
                }
                if (outcome == null) {
                    continue;
                }
                if (outcome instanceof CardExtractor.RetryLater r && r.noticeRejected()) {
                    retryLater++;
                    if (++consecutiveRejected < MAX_CONSECUTIVE_REJECTED) {
                        log.warn("notice {} rejected by llm; skipping: {}", noticeId, r.message());
                        continue;
                    }
                    log.warn("extraction batch stopped after {} rejected requests", consecutiveRejected);
                    BatchReport stopped = new BatchReport(extracted, needsOriginal, retryLater);
                    log.info("extraction finished: {}", stopped);
                    return stopped;
                }
                consecutiveRejected = 0;
                switch (outcome) {
                    case CardExtractor.Extracted e -> extracted++;
                    case CardExtractor.Rejected r -> needsOriginal++;
                    case CardExtractor.RetryLater r -> {
                        retryLater++;
                        log.warn("extraction batch stopped at notice {}: {}", noticeId, r.message());
                        BatchReport stopped = new BatchReport(extracted, needsOriginal, retryLater);
                        log.info("extraction finished: {}", stopped);
                        return stopped;
                    }
                }
            }
        } finally {
            batchRunning.set(false);
        }
        BatchReport report = new BatchReport(extracted, needsOriginal, retryLater);
        log.info("extraction finished: {}", report);
        return report;
    }

    /** 내부 API용. 상태와 관계없이 다시 추출한다. 배치 전체가 아니라 공지 단위 락만 잡는다. */
    public CardExtractor.Outcome reExtract(Long noticeId) {
        if (!llmProperties.configured()) {
            throw new BusinessException(ErrorCode.LLM_NOT_CONFIGURED);
        }
        return extractOne(noticeId, true, attachmentCollector.newRobotsPolicy());
    }

    private CardExtractor.Outcome extractOne(Long noticeId, boolean force, RobotsPolicy robots) {
        ReentrantLock lock = lockFor(noticeId);
        lock.lock();
        try {
            Notice notice = noticeRepository.findById(noticeId).orElse(null);
            if (notice == null) {
                if (force) {
                    throw new BusinessException(ErrorCode.NOTICE_NOT_FOUND);
                }
                return null;
            }
            if (!force && notice.getExtractionStatus() != ExtractionStatus.PENDING) {
                return null;
            }
            if (notice.getBodyHash() == null || (!hasBody(notice) && !notice.hasAttachments())) {
                if (force) {
                    throw new BusinessException(ErrorCode.NOTICE_BODY_MISSING);
                }
                return null;
            }

            String expectedBodyHash = notice.getBodyHash();
            List<AttachmentText> collectedAttachments = notice.getAttachmentTexts();
            if (notice.hasAttachments() && !notice.attachmentsCollected()) {
                List<AttachmentText> downloaded = attachmentCollector.collect(
                        notice.getTitle(), notice.getAttachments(), robots);
                Instant collectedAt = Instant.now();
                Boolean saved = transactionTemplate.execute(status -> {
                    Notice fresh = noticeRepository.findById(noticeId).orElse(null);
                    if (fresh == null || !Objects.equals(fresh.getBodyHash(), expectedBodyHash)) {
                        return false;
                    }
                    fresh.applyAttachmentTexts(downloaded, collectedAt);
                    noticeRepository.save(fresh);
                    return true;
                });
                if (!Boolean.TRUE.equals(saved)) {
                    log.info("notice {} bodyHash changed during attachment collection; keeping PENDING", noticeId);
                    return null;
                }
                collectedAttachments = downloaded;
            }

            Map<String, String> attachmentTexts = readTexts(collectedAttachments);
            if (!hasBody(notice) && attachmentTexts.isEmpty()) {
                log.info("notice {} has no body and no readable attachment", notice.getArticleNo());
                Instant now = Instant.now();
                transactionTemplate.executeWithoutResult(status -> {
                    Notice fresh = noticeRepository.findById(noticeId).orElse(null);
                    if (fresh == null || !Objects.equals(fresh.getBodyHash(), expectedBodyHash)) {
                        return;
                    }
                    fresh.markNeedsOriginal(ExtractionReason.ATTACHMENT_UNREADABLE, now);
                    noticeRepository.save(fresh);
                });
                return new CardExtractor.Rejected(List.of("본문이 없고 읽은 첨부도 없습니다."), 0,
                        ExtractionReason.ATTACHMENT_UNREADABLE);
            }

            LocalDate postedAt = notice.getPostedAt().atZone(KoreanTime.ZONE).toLocalDate();
            CardExtractor.Input input = new CardExtractor.Input(notice.getTitle(), notice.getDepartment(), postedAt,
                    notice.getBodyText(), attachmentTexts);
            CardExtractor.Outcome outcome = extractor.extract(input, LocalDate.now(KoreanTime.ZONE));
            Instant now = Instant.now();
            switch (outcome) {
                case CardExtractor.Extracted e -> {
                    SavedCard savedCard = transactionTemplate.execute(status -> {
                        Notice fresh = noticeRepository.findById(noticeId).orElse(null);
                        if (fresh == null || !Objects.equals(fresh.getBodyHash(), expectedBodyHash)) {
                            log.info("notice {} bodyHash changed during extraction; keeping PENDING", noticeId);
                            return null;
                        }
                        CardExtractor.Deadline deadline = e.deadline();
                        List<CardDiff.CardChange> changes = fresh.applyCard(
                                e.card(),
                                deadline == null ? null : deadline.at(),
                                deadline != null && deadline.allDay(),
                                now);
                        noticeRepository.save(fresh);
                        if (!changes.isEmpty()) {
                            noticeChangeRepository.saveAll(changes.stream()
                                    .map(change -> NoticeChange.of(fresh.getId(), fresh.getRevision(), change, now))
                                    .toList());
                            log.info("notice {} changed (revision {}): {}", fresh.getArticleNo(), fresh.getRevision(),
                                    fresh.getChangeSummary());
                        }
                        return new SavedCard(fresh, changes);
                    });
                    if (savedCard != null) {
                        noticeAlerts.onCardSaved(savedCard.notice(), savedCard.changes());
                    }
                }
                case CardExtractor.Rejected r -> {
                    log.info("notice {} needs original ({}): {}", notice.getArticleNo(), r.reason(), r.errors());
                    transactionTemplate.executeWithoutResult(status -> {
                        Notice fresh = noticeRepository.findById(noticeId).orElse(null);
                        if (fresh == null || !Objects.equals(fresh.getBodyHash(), expectedBodyHash)) {
                            return;
                        }
                        fresh.markNeedsOriginal(r.reason(), now);
                        noticeRepository.save(fresh);
                    });
                }
                case CardExtractor.RetryLater r -> {
                }
            }
            return outcome;
        } finally {
            lock.unlock();
        }
    }

    private static Map<String, String> readTexts(List<AttachmentText> texts) {
        Map<String, String> read = new LinkedHashMap<>();
        if (texts != null) {
            texts.stream().filter(AttachmentText::read).forEach(text -> read.putIfAbsent(text.name(), text.text()));
        }
        return read;
    }

    private static boolean hasBody(Notice notice) {
        return notice.getBodyText() != null && !notice.getBodyText().isBlank();
    }
}
