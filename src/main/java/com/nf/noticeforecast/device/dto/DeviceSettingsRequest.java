package com.nf.noticeforecast.device.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record DeviceSettingsRequest(
        @Valid @NotNull(message = "필수 값입니다.") AlertSettings alertSettings,
        @Valid @NotNull(message = "필수 값입니다.") MatchFilter matchFilter
) {
}
