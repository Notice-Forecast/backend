package com.nf.noticeforecast.device;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.dto.DeviceRegisterResponse;
import com.nf.noticeforecast.device.dto.DeviceResponse;
import com.nf.noticeforecast.device.dto.DeviceRestoreRequest;
import com.nf.noticeforecast.device.dto.DeviceRestoreResponse;
import com.nf.noticeforecast.device.dto.DeviceSettingsRequest;
import com.nf.noticeforecast.device.dto.PushSubscriptionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기기")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/devices", produces = MediaType.APPLICATION_JSON_VALUE)
public class DeviceController {

    private final DeviceService deviceService;

    @Operation(summary = "익명 기기 등록")
    @PostMapping
    public DeviceRegisterResponse register() {
        return deviceService.register();
    }

    @Operation(summary = "복구 코드로 기기 이어받기", description = "이전 푸시 구독은 삭제됩니다. IP당 분당 5회.")
    @PostMapping("/restore")
    public DeviceRestoreResponse restore(@Valid @RequestBody DeviceRestoreRequest request) {
        return deviceService.restore(request.restoreCode());
    }

    @Operation(summary = "내 기기 설정", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @GetMapping("/me")
    public DeviceResponse get(@CurrentDevice Device device) {
        return deviceService.get(device.getId());
    }

    @Operation(summary = "알림 설정·새 공지 조건 덮어쓰기", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @PutMapping("/me/settings")
    public DeviceResponse updateSettings(@CurrentDevice Device device, @Valid @RequestBody DeviceSettingsRequest request) {
        return deviceService.updateSettings(device.getId(), request);
    }

    @Operation(summary = "푸시 구독 등록·갱신", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/me/push-subscription")
    public void subscribePush(@CurrentDevice Device device, @Valid @RequestBody PushSubscriptionRequest request) {
        deviceService.subscribePush(device.getId(), request);
    }

    @Operation(summary = "푸시 구독 해제", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/push-subscription")
    public void unsubscribePush(@CurrentDevice Device device) {
        deviceService.unsubscribePush(device.getId());
    }

    @Operation(summary = "기기와 서버에 저장된 정보 삭제", security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    public void delete(@CurrentDevice Device device) {
        deviceService.delete(device.getId());
    }
}
