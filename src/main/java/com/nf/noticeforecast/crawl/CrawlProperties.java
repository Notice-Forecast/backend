package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.notice.SourceKind;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.crawl")
public record CrawlProperties(boolean enabled, List<String> include, List<Source> sources) {

    public CrawlProperties {
        include = include == null ? List.of() : include;
        sources = sources == null ? List.of() : sources;
    }

    public record Source(String name, String baseUrl, String listPath, SourceKind kind) {
    }
}
