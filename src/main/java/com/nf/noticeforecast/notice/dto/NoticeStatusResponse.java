package com.nf.noticeforecast.notice.dto;

import com.nf.noticeforecast.notice.MyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record NoticeStatusResponse(
        @Schema(example = "1024", requiredMode = Schema.RequiredMode.REQUIRED) Long noticeId,
        @Schema(nullable = true, example = "PLANNED", requiredMode = Schema.RequiredMode.REQUIRED) MyStatus status,
        @Schema(nullable = true, example = "2026-11-20T12:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt
) {
}
