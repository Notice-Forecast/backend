package com.nf.noticeforecast.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NoticeListResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NoticeListItem> items,
        @Schema(nullable = true, description = "다음 페이지 cursor. 마지막 페이지면 null",
                requiredMode = Schema.RequiredMode.REQUIRED) String nextCursor
) {
}
