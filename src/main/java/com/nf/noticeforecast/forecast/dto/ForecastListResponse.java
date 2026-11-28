package com.nf.noticeforecast.forecast.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nf.noticeforecast.forecast.FollowNotify;
import com.nf.noticeforecast.forecast.FollowSource;
import com.nf.noticeforecast.forecast.ForecastConfidence;
import com.nf.noticeforecast.notice.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record ForecastListResponse(
        @Schema(description = "내가 구독 중인 예보 계열", requiredMode = Schema.RequiredMode.REQUIRED)
        List<FollowingItem> following,
        @Schema(description = "추천 예보 계열(구독 중인 계열 제외). q가 있으면 검색 결과", requiredMode = Schema.RequiredMode.REQUIRED)
        List<RecommendedItem> recommended
) {
    public record FollowingItem(
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long seriesId,
            @Schema(example = "복수전공·부전공 변경 신청", requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(example = "ACADEMIC", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) NoticeCategory category,
            @Schema(example = "2026-11-24", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedFrom,
            @Schema(example = "2026-11-26", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedTo,
            @Schema(example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED) ForecastConfidence confidence,
            @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED) int pastCount,
            @Schema(example = "RECOMMENDED", requiredMode = Schema.RequiredMode.REQUIRED) FollowSource source,
            @JsonProperty("notify")
            @Schema(name = "notify", example = "IMMEDIATE", requiredMode = Schema.RequiredMode.REQUIRED) FollowNotify notifyMode
    ) {
    }

    public record RecommendedItem(
            @Schema(example = "31", requiredMode = Schema.RequiredMode.REQUIRED) Long seriesId,
            @Schema(example = "학술연구장학생 신청", requiredMode = Schema.RequiredMode.REQUIRED) String name,
            @Schema(example = "SCHOLARSHIP", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) NoticeCategory category,
            @Schema(example = "2026-11-27", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedFrom,
            @Schema(example = "2026-12-03", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedTo,
            @Schema(example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED) ForecastConfidence confidence,
            @Schema(example = "4", requiredMode = Schema.RequiredMode.REQUIRED) int pastCount
    ) {
    }
}
