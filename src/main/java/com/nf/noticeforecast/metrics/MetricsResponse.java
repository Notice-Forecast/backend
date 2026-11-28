package com.nf.noticeforecast.metrics;

import com.nf.noticeforecast.global.time.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /metrics 응답(CLAUDE.md 15.2, 11/28 결정). 실측을 한 번도 하지 않았으면 모든 항목이 null이다. */
public record MetricsResponse(
        @Schema(description = "실측 시각. 실측 전에는 null", example = "2026-12-01T09:00:00+09:00", nullable = true)
        OffsetDateTime computedAt,
        @Schema(description = "실측한 추출 모델. 실측 전에는 null", example = "claude-haiku-5-5", nullable = true) String model,
        @Schema(description = "표본 수. 실측 전에는 null", nullable = true) MetricsReport.Samples samples,
        @Schema(description = "추출 정확도. 실측 전에는 null", nullable = true) MetricsReport.Extraction extraction,
        @Schema(description = "판정 정확도. 페르소나 3명 × FULL 공지. 못 쟀으면 null", nullable = true) MetricsReport.Score verdict,
        @Schema(description = "사람 판정이 해당·조건부인 쌍 중 서비스가 숨긴 비율. 못 쟀으면 null", nullable = true)
        MetricsReport.HiddenRate hiddenWhileEligible,
        @Schema(description = "예보 적중률. 백테스트를 구현하기 전에는 null", nullable = true) MetricsReport.Forecast forecast,
        @Schema(description = "페르소나별 새 공지 알림량. 실측 전에는 null", nullable = true)
        List<MetricsReport.AlertVolume> alerts) {

    static MetricsResponse of(MetricsRun run) {
        MetricsReport report = run.getReport();
        return new MetricsResponse(KoreanTime.from(run.getComputedAt()), report.model(), report.samples(),
                report.extraction(), report.verdict(), report.hiddenWhileEligible(), report.forecast(), report.alerts());
    }

    static MetricsResponse empty() {
        return new MetricsResponse(null, null, null, null, null, null, null, null);
    }
}
