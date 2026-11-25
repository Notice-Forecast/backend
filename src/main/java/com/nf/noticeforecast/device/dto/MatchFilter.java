package com.nf.noticeforecast.device.dto;

import com.nf.noticeforecast.device.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MatchFilter(
        @Schema(description = "주전공", example = "컴퓨터정보공학부")
        @NotBlank(message = "학과를 입력해 주세요.")
        @Size(max = 100, message = "학과 이름이 너무 깁니다.")
        String department,

        @Schema(description = "복수·부·융합전공. 없으면 빈 배열", example = "[\"경영학과\"]")
        @NotNull(message = "필수 값입니다.")
        @Size(max = 3, message = "추가 전공은 3개까지 고를 수 있습니다.")
        List<@NotBlank(message = "학과를 입력해 주세요.") @Size(max = 100, message = "학과 이름이 너무 깁니다.") String> extraDepartments,

        @Schema(example = "3")
        @NotNull(message = "필수 값입니다.")
        @Min(value = 1, message = "1 이상이어야 합니다.")
        @Max(value = 6, message = "6 이하여야 합니다.")
        Integer grade,

        @Schema(example = "ENROLLED")
        @NotNull(message = "필수 값입니다.")
        EnrollmentStatus enrollmentStatus
) {
    public MatchFilter normalized() {
        String main = department.trim();
        List<String> extras = extraDepartments.stream()
                .map(String::trim)
                .filter(extra -> !extra.equals(main))
                .distinct()
                .toList();
        return new MatchFilter(main, extras, grade, enrollmentStatus);
    }
}
