package com.nf.noticeforecast.forecast.dto;

import com.nf.noticeforecast.forecast.FollowNotify;
import com.nf.noticeforecast.forecast.FollowSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public class ForecastFollowRequest {

    @Schema(description = "알림 방식. 현재 IMMEDIATE(게시 즉시)만 지원합니다.", example = "IMMEDIATE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "필수 값입니다.")
    private FollowNotify notify;

    @Schema(description = "구독 경로(선택). 추천 목록이면 RECOMMENDED, 검색이면 MANUAL. 생략하면 서버가 판별합니다.",
            example = "RECOMMENDED", nullable = true)
    private FollowSource source;

    public FollowNotify getNotify() {
        return notify;
    }

    public void setNotify(FollowNotify notify) {
        this.notify = notify;
    }

    public FollowSource getSource() {
        return source;
    }

    public void setSource(FollowSource source) {
        this.source = source;
    }
}
