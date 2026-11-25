package com.nf.noticeforecast.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @Schema(example = "https://fcm.googleapis.com/fcm/send/abc")
        @NotBlank(message = "필수 값입니다.")
        @Size(max = 2048, message = "주소가 너무 깁니다.")
        @Pattern(regexp = "https://.+", message = "https 주소여야 합니다.")
        String endpoint,

        @Valid @NotNull(message = "필수 값입니다.") Keys keys
) {
    public record Keys(
            @NotBlank(message = "필수 값입니다.") @Size(max = 255, message = "값이 너무 깁니다.") String p256dh,
            @NotBlank(message = "필수 값입니다.") @Size(max = 255, message = "값이 너무 깁니다.") String auth
    ) {
    }
}
