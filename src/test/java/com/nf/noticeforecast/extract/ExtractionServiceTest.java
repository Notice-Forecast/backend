package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.attachment.AttachmentCollector;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.notice.ExtractionReason;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeChangeRepository;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.push.NoticeAlerts;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ExtractionServiceTest {

    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final NoticeChangeRepository noticeChangeRepository = mock(NoticeChangeRepository.class);
    private final NoticeAlerts noticeAlerts = mock(NoticeAlerts.class);
    private final CardExtractor extractor = mock(CardExtractor.class);
    private final AttachmentCollector attachmentCollector = mock(AttachmentCollector.class);
    private final LlmProperties llmProperties = new LlmProperties("test-key", "claude-haiku-5-5", "medium", 4096);

    private ExtractionService service() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new ExtractionService(noticeRepository, noticeChangeRepository, noticeAlerts, extractor,
                attachmentCollector, llmProperties, new TransactionTemplate(txManager));
    }

    private static Notice pendingNotice(long id, String bodyHash) {
        Notice notice = Notice.builder()
                .sourceId(1L)
                .articleNo(id)
                .title("공지 " + id)
                .department("학생지원팀")
                .postedAt(Instant.parse("2026-11-27T00:00:00Z"))
                .sourceUrl("https://www.catholic.ac.kr/notice/" + id)
                .bodyText("재학생 대상 신청 안내입니다.")
                .bodyHash(bodyHash)
                .attachments(List.of())
                .extractionStatus(ExtractionStatus.PENDING)
                .now(Instant.parse("2026-11-27T00:00:00Z"))
                .build();
        ReflectionTestUtils.setField(notice, "id", id);
        return notice;
    }

    private static Card sampleCard() {
        return new Card("요약", true, List.of(), List.of(), List.of(), null);
    }

    @Test
    void preservesSeriesIdUpdatedDuringExtraction() {
        Notice initial = pendingNotice(1L, "hash-1");
        Notice freshWithSeries = pendingNotice(1L, "hash-1");
        ReflectionTestUtils.setField(freshWithSeries, "seriesId", 99L);

        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(initial), Optional.of(freshWithSeries));
        when(extractor.extract(any(), any())).thenReturn(new CardExtractor.Extracted(sampleCard(), null, "haiku", 1));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.extracted()).isEqualTo(1);
        assertThat(freshWithSeries.getSeriesId()).isEqualTo(99L);
        assertThat(freshWithSeries.getExtractionStatus()).isEqualTo(ExtractionStatus.DONE);
        verify(noticeRepository).save(freshWithSeries);
        verify(noticeRepository, never()).save(initial);
    }

    @Test
    void skipsSavingWhenBodyHashChangedDuringExtraction() {
        Notice initial = pendingNotice(1L, "hash-old");
        Notice modified = pendingNotice(1L, "hash-new");

        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(initial), Optional.of(modified));
        when(extractor.extract(any(), any())).thenReturn(new CardExtractor.Extracted(sampleCard(), null, "haiku", 1));

        service().extractPending();

        assertThat(modified.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
        verify(noticeRepository, never()).save(any());
        verify(noticeAlerts, never()).onCardSaved(any(), any());
    }

    @Test
    void stopsBatchImmediatelyOnRetryLater() {
        Notice n1 = pendingNotice(1L, "h1");
        Notice n2 = pendingNotice(2L, "h2");
        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L, 2L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n1));
        when(noticeRepository.findById(2L)).thenReturn(Optional.of(n2));
        when(extractor.extract(any(), any())).thenReturn(new CardExtractor.RetryLater("429"));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.retryLater()).isEqualTo(1);
        verify(extractor, times(1)).extract(any(), any());
        verify(noticeRepository, never()).findById(2L);
        assertThat(n1.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
    }

    @Test
    void skipsNoticeRejectedWith400AndContinues() {
        Notice n1 = pendingNotice(1L, "h1");
        Notice n2 = pendingNotice(2L, "h2");
        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L, 2L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n1));
        when(noticeRepository.findById(2L)).thenReturn(Optional.of(n2));
        when(extractor.extract(any(), any())).thenReturn(new CardExtractor.RetryLater("400", true),
                new CardExtractor.RetryLater("429"));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.retryLater()).isEqualTo(2);
        verify(extractor, times(2)).extract(any(), any());
        assertThat(n1.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
    }

    @Test
    void stopsBatchAfterConsecutiveRejectedRequests() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(ids);
        for (Long id : ids) {
            when(noticeRepository.findById(id)).thenReturn(Optional.of(pendingNotice(id, "h" + id)));
        }
        when(extractor.extract(any(), any())).thenReturn(new CardExtractor.RetryLater("400", true));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.retryLater()).isEqualTo(ExtractionService.MAX_CONSECUTIVE_REJECTED);
        verify(extractor, times(ExtractionService.MAX_CONSECUTIVE_REJECTED)).extract(any(), any());
        verify(noticeRepository, never()).findById(4L);
    }

    @Test
    void marksNeedsOriginalOnInputTooLong() {
        Notice n1 = pendingNotice(1L, "h1");
        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n1), Optional.of(n1));
        when(extractor.extract(any(), any())).thenReturn(
                new CardExtractor.Rejected(List.of("너무 김"), 1, ExtractionReason.ATTACHMENT_UNREADABLE));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.needsOriginal()).isEqualTo(1);
        assertThat(n1.getExtractionStatus()).isEqualTo(ExtractionStatus.NEEDS_ORIGINAL);
        assertThat(n1.getExtractionReason()).isEqualTo(ExtractionReason.ATTACHMENT_UNREADABLE);
        verify(noticeRepository).save(n1);
    }

    @Test
    void continuesBatchAfterUnexpectedExceptionOnOneNotice() {
        Notice n1 = pendingNotice(1L, "h1");
        Notice n2 = pendingNotice(2L, "h2");
        when(noticeRepository.findExtractionCandidateIds(any())).thenReturn(List.of(1L, 2L));
        when(noticeRepository.findById(1L)).thenReturn(Optional.of(n1));
        when(noticeRepository.findById(2L)).thenReturn(Optional.of(n2), Optional.of(n2));
        when(extractor.extract(any(), any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new CardExtractor.Extracted(sampleCard(), null, "haiku", 1));

        ExtractionService.BatchReport report = service().extractPending();

        assertThat(report.extracted()).isEqualTo(1);
        assertThat(n2.getExtractionStatus()).isEqualTo(ExtractionStatus.DONE);
        verify(noticeRepository).save(eq(n2));
    }
}
