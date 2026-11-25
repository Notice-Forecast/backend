package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.CurrentDevice;
import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.notice.dto.NoticeStatusRequest;
import com.nf.noticeforecast.notice.dto.NoticeStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내 상태")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/devices/me/statuses", produces = MediaType.APPLICATION_JSON_VALUE)
public class NoticeStatusController {

    private final NoticeStatusService noticeStatusService;

    @Operation(summary = "신청 예정·신청 완료·관심 없음 저장", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @PutMapping("/{noticeId}")
    public NoticeStatusResponse change(@CurrentDevice Device device, @PathVariable Long noticeId,
                                       @Valid @RequestBody NoticeStatusRequest request) {
        return noticeStatusService.change(device.getId(), noticeId, request.getStatus());
    }
}
