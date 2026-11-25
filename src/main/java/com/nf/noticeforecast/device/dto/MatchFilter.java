package com.nf.noticeforecast.device.dto;

import com.nf.noticeforecast.device.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MatchFilter(
        @Schema(example = "컴퓨터정보공학부")
        @NotBlank(message = "학과를 입력해 주세요.")
        @Size(max = 100, message = "학과 이름이 너무 깁니다.")
        String department,

        @Schema(example = "3")
        @NotNull(message = "필수 값입니다.")
        @Min(value = 1, message = "1 이상이어야 합니다.")
        @Max(value = 6, message = "6 이하여야 합니다.")
        Integer grade,

        @Schema(example = "ENROLLED")
        @NotNull(message = "필수 값입니다.")
        EnrollmentStatus enrollmentStatus
) {
}
