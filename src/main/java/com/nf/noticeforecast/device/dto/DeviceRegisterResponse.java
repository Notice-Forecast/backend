package com.nf.noticeforecast.device.dto;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.global.time.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record DeviceRegisterResponse(
        @Schema(example = "6f1c2b9e-8a4d-4f3e-9b7a-2d5c1e0f4a83", requiredMode = Schema.RequiredMode.REQUIRED) String deviceId,
        @Schema(example = "K7QF-2M9X", requiredMode = Schema.RequiredMode.REQUIRED) String restoreCode,
        @Schema(example = "2026-11-20T12:00:00+09:00", requiredMode = Schema.RequiredMode.REQUIRED) OffsetDateTime createdAt
) {
    public static DeviceRegisterResponse from(Device device) {
        return new DeviceRegisterResponse(device.getId(), device.getRestoreCode(), KoreanTime.from(device.getCreatedAt()));
    }
}
