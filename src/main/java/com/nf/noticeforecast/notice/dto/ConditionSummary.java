package com.nf.noticeforecast.notice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "판정용 자격 조건. 목록에서는 근거 문장(evidence)을 뺀다.")
public record ConditionSummary(
        @Schema(example = "ENROLLMENT_STATUS", requiredMode = Schema.RequiredMode.REQUIRED) String field,
        @Schema(example = "IN", requiredMode = Schema.RequiredMode.REQUIRED) String op,
        @Schema(example = "[\"ENROLLED\"]", requiredMode = Schema.RequiredMode.REQUIRED) List<String> values
) {
}
