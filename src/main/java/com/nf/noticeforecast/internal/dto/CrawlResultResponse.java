package com.nf.noticeforecast.internal.dto;

import com.nf.noticeforecast.crawl.NoticeCrawler.CrawlReport;
import io.swagger.v3.oas.annotations.media.Schema;

public record CrawlResultResponse(
        @Schema(description = "목록에서 본 공지 수", example = "528") int listed,
        @Schema(description = "새로 저장한 공지 수", example = "12") int saved,
        @Schema(description = "다시 확인한 저장된 공지 수", example = "0") int rechecked,
        @Schema(description = "본문이 바뀐 공지 수", example = "0") int changed,
        @Schema(description = "상세를 받지 못한 공지 수", example = "0") int failedDetails,
        @Schema(description = "목록을 받지 못한 사이트 수", example = "0") int failedSources,
        @Schema(description = "robots.txt로 건너뛴 수", example = "0") int skippedByRobots,
        @Schema(description = "저장한 학기 수 (과거 수집에서만)", example = "7") int terms
) {
    public static CrawlResultResponse from(CrawlReport report) {
        return new CrawlResultResponse(report.getListed(), report.getSaved(), report.getRechecked(),
                report.getChanged(), report.getFailedDetails(), report.getFailedSources(), report.getSkippedByRobots(), report.getTerms());
    }
}
