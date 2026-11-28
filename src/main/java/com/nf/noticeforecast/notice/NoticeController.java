package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.CurrentDevice;
import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.notice.dto.NoticeDetailResponse;
import com.nf.noticeforecast.notice.dto.NoticeListResponse;
import com.nf.noticeforecast.notice.dto.ReportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공지")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/notices", produces = MediaType.APPLICATION_JSON_VALUE)
public class NoticeController {

    private final NoticeQueryService noticeQueryService;
    private final ReportService reportService;

    @Operation(summary = "공지 목록",
            description = "게시일 최신순. 본교 공지와 기기의 주전공·추가 전공에 해당하는 학과 공지. 기기 ID나 matchFilter가 없으면 본교만.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @GetMapping
    public NoticeListResponse list(
            @CurrentDevice(required = false) Device device,
            @Parameter(description = "분류 필터") @RequestParam(required = false) NoticeCategory category,
            @Parameter(description = "마감 지난 공지 제외") @RequestParam(defaultValue = "true") boolean openOnly,
            @Parameter(description = "이 시각 이후 바뀐 공지만. +는 %2B로 인코딩하거나 Z(UTC)로 보냅니다",
                    example = "2026-11-28T00:00:00+09:00")
            @RequestParam(required = false) String updatedSince,
            @Parameter(description = "내 상태 필터. X-Device-Id 필요") @RequestParam(required = false) MyStatus myStatus,
            @Parameter(description = "관심 없음으로 고른 공지 포함") @RequestParam(defaultValue = "false") boolean includeDismissed,
            @Parameter(description = "다음 페이지 cursor") @RequestParam(required = false) String cursor,
            @Parameter(description = "기본 20, 최대 50") @RequestParam(required = false) Integer size) {
        return noticeQueryService.list(device, new NoticeQueryService.ListQuery(category, openOnly,
                NoticeQueryService.parseUpdatedSince(updatedSince), myStatus, includeDismissed, cursor, size));
    }

    @Operation(summary = "공지 상세", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @GetMapping("/{id}")
    public NoticeDetailResponse detail(@CurrentDevice(required = false) Device device, @PathVariable Long id) {
        return noticeQueryService.detail(device, id);
    }

    @Operation(summary = "판정·추출 오류 신고", description = "같은 기기가 같은 공지를 다시 신고하면 덮어씁니다. 응답 본문 없이 201.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @PostMapping("/{id}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public void report(@CurrentDevice Device device, @PathVariable Long id, @Valid @RequestBody ReportRequest request) {
        reportService.report(device.getId(), id, request);
    }
}
