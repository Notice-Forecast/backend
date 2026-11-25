package com.nf.noticeforecast.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeviceRestoreResponse(
        @Schema(example = "6f1c2b9e-8a4d-4f3e-9b7a-2d5c1e0f4a83", requiredMode = Schema.RequiredMode.REQUIRED) String deviceId
) {
}
