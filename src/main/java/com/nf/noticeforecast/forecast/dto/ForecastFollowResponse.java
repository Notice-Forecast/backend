package com.nf.noticeforecast.forecast.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nf.noticeforecast.forecast.FollowNotify;
import com.nf.noticeforecast.forecast.FollowSource;
import io.swagger.v3.oas.annotations.media.Schema;

public record ForecastFollowResponse(
        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long seriesId,
        @JsonProperty("notify")
        @Schema(name = "notify", example = "IMMEDIATE", requiredMode = Schema.RequiredMode.REQUIRED) FollowNotify notifyMode,
        @Schema(example = "RECOMMENDED", requiredMode = Schema.RequiredMode.REQUIRED) FollowSource source
) {
}
