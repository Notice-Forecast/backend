package com.nf.noticeforecast.notice.dto;

import com.nf.noticeforecast.notice.ReportReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportRequest(
        @Schema(example = "WRONG_CONDITION", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "reason은 필수 값입니다.") ReportReason reason,
        @Schema(nullable = true, description = "최대 200자. 개인정보를 적지 않도록 화면에서 안내", example = "평점 조건이 2.5 이상으로 바뀜")
        @Size(max = 200, message = "200자 이하로 적어 주세요.") String comment
) {
}
