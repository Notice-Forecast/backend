package com.nf.noticeforecast.notice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.notice.ExtractionReason;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.MyStatus;
import com.nf.noticeforecast.notice.NoticeAttachment;
import com.nf.noticeforecast.notice.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record NoticeDetailResponse(
        @Schema(example = "1024", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "277795", requiredMode = Schema.RequiredMode.REQUIRED) Long articleNo,
        @Schema(example = "2026학년도 겨울 계절학기 수강신청 안내", requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(nullable = true, example = "학사지원팀", requiredMode = Schema.RequiredMode.REQUIRED) String department,
        @Schema(example = "가톨릭대학교", requiredMode = Schema.RequiredMode.REQUIRED) String sourceName,
        @Schema(nullable = true, example = "ACADEMIC", requiredMode = Schema.RequiredMode.REQUIRED) NoticeCategory category,
        @Schema(example = "2026-11-17T00:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime postedAt,
        @Schema(example = "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=277795",
                requiredMode = Schema.RequiredMode.REQUIRED) String sourceUrl,
        @Schema(nullable = true, description = "신청 마감. 원문 확인 필요 공지는 제목에서 읽은 마감. 날짜만 있으면 날짜만",
                example = "2026-11-21T17:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) String deadlineAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<NoticeAttachment> attachments,
        @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int revision,
        @Schema(example = "2026-11-18T14:10:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime updatedAt,
        @Schema(description = "시연용 가상 변경이면 true. 상세 화면에만 '가상' 표기", example = "false",
                requiredMode = Schema.RequiredMode.REQUIRED) boolean isSimulated,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Extraction extraction,
        @Schema(nullable = true, description = "NEEDS_ORIGINAL이거나 아직 추출 전이면 null",
                requiredMode = Schema.RequiredMode.REQUIRED) Card card,
        @Schema(nullable = true, example = "마감이 11/16에서 11/18로 연장됐어요.",
                requiredMode = Schema.RequiredMode.REQUIRED) String changeSummary,
        @Schema(description = "revision이 오른 기록, 최신순", requiredMode = Schema.RequiredMode.REQUIRED) List<Change> changes,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "같은 계열의 직전 회차 비교. 없으면 필드를 생략") Object previousRound,
        @Schema(nullable = true, example = "PLANNED", requiredMode = Schema.RequiredMode.REQUIRED) MyStatus myStatus
) {

    public record Extraction(
            @Schema(example = "DONE", requiredMode = Schema.RequiredMode.REQUIRED) ExtractionStatus status,
            @Schema(nullable = true, example = "IMAGE_ONLY", requiredMode = Schema.RequiredMode.REQUIRED) ExtractionReason reason,
            @Schema(nullable = true, example = "2026-11-18T14:10:00+09:00",
                    requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime extractedAt
    ) {
    }

    public record Change(
            @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED) int revision,
            @Schema(example = "2026-11-15T21:04:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime changedAt,
            @Schema(example = "APPLY_END", requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(nullable = true, example = "2026-11-16T18:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) String before,
            @Schema(nullable = true, example = "2026-11-18T18:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) String after
    ) {
    }
}
