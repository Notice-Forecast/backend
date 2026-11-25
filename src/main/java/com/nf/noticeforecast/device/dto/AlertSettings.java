package com.nf.noticeforecast.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AlertSettings(
        @Schema(description = "새 공지 알림", example = "true") @NotNull(message = "필수 값입니다.") Boolean newNotice,
        @Schema(description = "준비 시작 알림", example = "true") @NotNull(message = "필수 값입니다.") Boolean prepStart,
        @Schema(description = "공지 변경 알림", example = "true") @NotNull(message = "필수 값입니다.") Boolean noticeChanged,
        @Schema(description = "마감 임박 알림", example = "true") @NotNull(message = "필수 값입니다.") Boolean deadline
) {
}
