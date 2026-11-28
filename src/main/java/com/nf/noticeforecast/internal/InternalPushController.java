package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.DeviceRepository;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.push.PushDispatcher;
import com.nf.noticeforecast.push.PushPayload;
import com.nf.noticeforecast.push.PushType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/push", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalPushController {

    private final PushDispatcher pushDispatcher;
    private final DeviceRepository deviceRepository;
    private final NoticeRepository noticeRepository;

    public record TestPushRequest(
            @Schema(example = "6f1c2b9e-8a4d-4f3e-9b7a-2d5c1e0f4a83") @NotBlank(message = "필수 값입니다.") String deviceId,
            @Schema(example = "DEADLINE") @NotNull(message = "필수 값입니다.") PushType type,
            @Schema(example = "1024") @NotNull(message = "필수 값입니다.") Long noticeId) {
    }

    public record TestPushResponse(
            @Schema(description = "SENT, NO_SUBSCRIPTION, GONE, FAILED, NOT_CONFIGURED", example = "SENT")
            PushDispatcher.Delivery result) {
    }

    @Operation(summary = "테스트 푸시",
            description = "지정한 기기에 알림 종류별 테스트 푸시를 보낸다. 알림 설정과 중복 검사 없이 보내고, 알림량 실측에 섞이지 않도록 push_log에는 남기지 않는다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/test")
    public TestPushResponse test(@Valid @RequestBody TestPushRequest request) {
        if (!deviceRepository.existsById(request.deviceId())) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND);
        }
        Notice notice = noticeRepository.findById(request.noticeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        PushPayload payload = PushPayload.forNotice(request.type(), notice.getTitle(),
                "테스트 알림입니다 (" + request.type() + ")", notice.getId());
        return new TestPushResponse(pushDispatcher.sendDirect(request.deviceId(), payload));
    }
}
