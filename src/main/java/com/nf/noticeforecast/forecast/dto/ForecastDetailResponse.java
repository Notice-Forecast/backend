package com.nf.noticeforecast.forecast.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nf.noticeforecast.forecast.FollowNotify;
import com.nf.noticeforecast.forecast.ForecastConfidence;
import com.nf.noticeforecast.notice.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record ForecastDetailResponse(
        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long seriesId,
        @Schema(example = "복수전공·부전공 변경 신청", requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(example = "ACADEMIC", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) NoticeCategory category,
        @Schema(example = "2026-11-24", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedFrom,
        @Schema(example = "2026-11-26", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDate expectedTo,
        @Schema(example = "HIGH", requiredMode = Schema.RequiredMode.REQUIRED) ForecastConfidence confidence,
        @Schema(example = "최근 4개 학기 평균 학기 종료 4주 전에 올라왔어요.", requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(description = "지난 회차(최신순)", requiredMode = Schema.RequiredMode.REQUIRED) List<PastRound> past,
        @Schema(description = "지난 회차 카드에서 뽑은 미리 준비할 서류", requiredMode = Schema.RequiredMode.REQUIRED) List<String> prepare,
        @Schema(description = "내 구독 상태. 구독 전이거나 X-Device-Id가 없으면 null", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED) FollowInfo follow
) {
    public record PastRound(
            @Schema(example = "2026-1", requiredMode = Schema.RequiredMode.REQUIRED) String term,
            @Schema(example = "2026-05-26", requiredMode = Schema.RequiredMode.REQUIRED) LocalDate postedAt,
            @Schema(example = "812", requiredMode = Schema.RequiredMode.REQUIRED) Long noticeId
    ) {
    }

    public record FollowInfo(
            @JsonProperty("notify")
            @Schema(name = "notify", example = "IMMEDIATE", requiredMode = Schema.RequiredMode.REQUIRED) FollowNotify notifyMode
    ) {
    }
}
