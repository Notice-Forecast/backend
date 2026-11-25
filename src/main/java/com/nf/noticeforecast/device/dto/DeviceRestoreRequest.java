package com.nf.noticeforecast.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRestoreRequest(
        @Schema(example = "K7QF-2M9X")
        @NotBlank(message = "복구 코드를 입력해 주세요.")
        @Size(max = 20, message = "복구 코드가 너무 깁니다.")
        String restoreCode
) {
}
