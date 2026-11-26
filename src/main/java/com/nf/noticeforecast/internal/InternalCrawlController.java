package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.crawl.NoticeCrawler;
import com.nf.noticeforecast.internal.dto.CrawlResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/crawl", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalCrawlController {

    private final NoticeCrawler crawler;

    @Operation(summary = "크롤링 1회 즉시 실행", description = "1시간마다 도는 새 공지 확인과 같다. 끝날 때까지 기다린다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/run")
    public CrawlResultResponse run() {
        return CrawlResultResponse.from(crawler.crawlNew());
    }

    @Operation(summary = "과거 공지 일괄 수집", description = "학교 공지 전체와 컴퓨터정보공학부 2019년 이후 목록에서 제목·게시일·부서만 저장한다. 이미 있는 공지는 건너뛴다. 끝날 때까지 기다린다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/backfill")
    public CrawlResultResponse backfill() {
        return CrawlResultResponse.from(crawler.backfill());
    }
}
