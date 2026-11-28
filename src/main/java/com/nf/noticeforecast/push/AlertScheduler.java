package com.nf.noticeforecast.push;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 준비 시작·마감 임박 알림 일정(CLAUDE.md 9.3). 로컬에서 크롤러를 끄면 함께 꺼진다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.crawl", name = "enabled", havingValue = "true")
public class AlertScheduler {

    private final NoticeAlerts noticeAlerts;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void prepStart() {
        noticeAlerts.sendPrepStarts();
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "PT10M")
    public void deadline() {
        noticeAlerts.sendDeadlines();
    }
}
