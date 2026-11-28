package com.nf.noticeforecast.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record DepartmentListResponse(
        @Schema(description = "수집 대상 학과 목록(가나다순)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DepartmentItem> items
) {
    public record DepartmentItem(
            @Schema(description = "학과 이름", example = "컴퓨터정보공학부", requiredMode = Schema.RequiredMode.REQUIRED)
            String name
    ) {
    }
}
