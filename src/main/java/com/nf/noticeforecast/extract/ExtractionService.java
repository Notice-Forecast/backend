package com.nf.noticeforecast.extract;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ExtractionReason;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private static final Duration RECENT = Duration.ofDays(30);

    private final NoticeRepository noticeRepository;
    private final CardExtractor extractor;
    private final LlmProperties llmProperties;

    public record BatchReport(int extracted, int needsOriginal, int retryLater) {
    }

    /** 최근 30일 글 중 아직 추출하지 않은 공지를 카드로 바꾼다. 호출이 실패한 공지는 PENDING으로 남아 다음 배치에서 다시 시도한다. */
    public synchronized BatchReport extractPending() {
        if (!llmProperties.configured()) {
            log.warn("LLM api key is not set; skip extraction");
            return new BatchReport(0, 0, 0);
        }
        int extracted = 0;
        int needsOriginal = 0;
        int retryLater = 0;
        for (Notice notice : noticeRepository.findExtractionCandidates(Instant.now().minus(RECENT))) {
            switch (extractAndSave(notice)) {
                case CardExtractor.Extracted e -> extracted++;
                case CardExtractor.Rejected r -> needsOriginal++;
                case CardExtractor.RetryLater r -> retryLater++;
            }
        }
        BatchReport report = new BatchReport(extracted, needsOriginal, retryLater);
        log.info("extraction finished: {}", report);
        return report;
    }

    /** 내부 API용. 상태와 관계없이 본문이 있는 공지를 다시 추출한다. */
    public synchronized CardExtractor.Outcome reExtract(Long noticeId) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        if (notice.getBodyText() == null || notice.getBodyText().isBlank()) {
            throw new BusinessException(ErrorCode.NOTICE_BODY_MISSING);
        }
        if (!llmProperties.configured()) {
            throw new BusinessException(ErrorCode.LLM_NOT_CONFIGURED);
        }
        return extractAndSave(notice);
    }

    private CardExtractor.Outcome extractAndSave(Notice notice) {
        LocalDate postedAt = notice.getPostedAt().atZone(KoreanTime.ZONE).toLocalDate();
        CardExtractor.Input input = new CardExtractor.Input(notice.getTitle(), notice.getDepartment(), postedAt,
                notice.getBodyText(), Map.of());
        CardExtractor.Outcome outcome = extractor.extract(input, LocalDate.now(KoreanTime.ZONE));
        Instant now = Instant.now();
        switch (outcome) {
            case CardExtractor.Extracted e -> {
                CardExtractor.Deadline deadline = e.deadline();
                notice.applyCard(e.card(), deadline == null ? null : deadline.at(),
                        deadline != null && deadline.allDay(), now);
                noticeRepository.save(notice);
            }
            case CardExtractor.Rejected r -> {
                log.info("notice {} needs original: {}", notice.getArticleNo(), r.errors());
                notice.markNeedsOriginal(ExtractionReason.VALIDATION_FAILED, now);
                noticeRepository.save(notice);
            }
            case CardExtractor.RetryLater r -> {
            }
        }
        return outcome;
    }
}
