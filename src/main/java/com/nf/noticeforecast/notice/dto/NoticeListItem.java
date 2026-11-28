package com.nf.noticeforecast.notice.dto;

import com.nf.noticeforecast.notice.ExtractionReason;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.MyStatus;
import com.nf.noticeforecast.notice.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record NoticeListItem(
        @Schema(example = "1024", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "2026학년도 겨울 계절학기 수강신청 안내", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(nullable = true, example = "학사지원팀", requiredMode = Schema.RequiredMode.REQUIRED) String department,
        @Schema(example = "가톨릭대학교", requiredMode = Schema.RequiredMode.REQUIRED) String sourceName,
        @Schema(nullable = true, example = "ACADEMIC", requiredMode = Schema.RequiredMode.REQUIRED) NoticeCategory category,
        @Schema(example = "2026-11-17T00:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime postedAt,
        @Schema(nullable = true, description = "신청 마감. 시각이 있으면 +09:00 시각, 날짜만 있으면 2026-11-21처럼 날짜만",
                example = "2026-11-21T17:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) String deadlineAt,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(description = "게시 48시간 이내", example = "false", requiredMode = Schema.RequiredMode.REQUIRED) boolean isNew,
        @Schema(example = "DONE", requiredMode = Schema.RequiredMode.REQUIRED) ExtractionStatus extractionStatus,
        @Schema(nullable = true, description = "NEEDS_ORIGINAL일 때 사유", example = "IMAGE_ONLY",
                requiredMode = Schema.RequiredMode.REQUIRED) ExtractionReason extractionReason,
        @Schema(nullable = true, example = "마감이 11/16에서 11/18로 연장됐어요.",
                requiredMode = Schema.RequiredMode.REQUIRED) String changeSummary,
        @Schema(nullable = true, description = "카드가 없으면 null. false면 학부생 대상이 아니라 숨김",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED) Boolean forStudents,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<ConditionSummary> conditions,
        @Schema(nullable = true, example = "PLANNED", requiredMode = Schema.RequiredMode.REQUIRED) MyStatus myStatus,
        @Schema(nullable = true, example = "2026-11-17T21:30:00+09:00",
                requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime myStatusUpdatedAt
) {
}
