package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.extract.ExtractionService;
import com.nf.noticeforecast.forecast.SeriesLinker;
import com.nf.noticeforecast.notice.SourceKind;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 학교 서버 부담을 줄이려고 새 공지 확인과 변경 확인을 나눈다(11/26 결정).
 * 스케줄러 스레드가 하나라 세 작업이 겹쳐 돌지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.crawl", name = "enabled", havingValue = "true")
public class CrawlScheduler {

    private final NoticeCrawler crawler;
    private final ExtractionService extractionService;
    private final SeriesLinker seriesLinker;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void crawlNewNotices() {
        crawler.crawlNew();
        extractionService.extractPending();
        // 처음에는 과거 이력까지 수백 번 호출해 오래 걸린다. 정기 작업 스레드를 막지 않도록 백그라운드로 시작만 한다.
        seriesLinker.startInBackground();
    }

    @Scheduled(initialDelayString = "PT6H", fixedDelayString = "PT6H")
    public void recheckUniversityNotices() {
        crawler.recheckUniversityAndActive();
        extractionService.extractPending();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void recheckDepartmentNotices() {
        crawler.recheck(SourceKind.DEPARTMENT);
        extractionService.extractPending();
    }
}
