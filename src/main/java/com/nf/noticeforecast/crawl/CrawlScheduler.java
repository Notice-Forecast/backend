package com.nf.noticeforecast.crawl;

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

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1H")
    public void crawlNewNotices() {
        crawler.crawlNew();
    }

    @Scheduled(initialDelayString = "PT6H", fixedDelayString = "PT6H")
    public void recheckUniversityNotices() {
        crawler.recheck(SourceKind.UNIVERSITY);
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void recheckDepartmentNotices() {
        crawler.recheck(SourceKind.DEPARTMENT);
    }
}
