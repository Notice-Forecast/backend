package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NoticeSourceSync {

    private final CrawlProperties properties;
    private final NoticeSourceRepository repository;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void sync() {
        Set<Long> configured = new HashSet<>();
        for (CrawlProperties.Source source : properties.sources()) {
            NoticeSource entity = repository.findByBaseUrlAndListPath(source.baseUrl(), source.listPath())
                    .orElseGet(() -> repository.save(
                            NoticeSource.of(source.name(), source.baseUrl(), source.listPath(), source.kind())));
            entity.update(source.name(), source.kind(), true);
            configured.add(entity.getId());
        }
        repository.findAll().stream()
                .filter(entity -> !configured.contains(entity.getId()))
                .forEach(entity -> entity.update(entity.getName(), entity.getKind(), false));
    }
}
