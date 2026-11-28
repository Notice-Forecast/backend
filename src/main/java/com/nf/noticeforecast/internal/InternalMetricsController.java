package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.metrics.MetricsResponse;
import com.nf.noticeforecast.metrics.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalMetricsController {

    private final MetricsService metricsService;

    public record ReviewRequest(
            @Schema(description = "AI 요약이 정답 대상과 맞으면 true. 아직 안 봤으면 null", example = "true") Boolean targetCorrect,
            @Schema(description = "정답 서류 중 AI가 맞힌 개수(FULL만). BASIC이거나 아직 안 봤으면 null", example = "2")
            @Min(value = 0, message = "0 이상이어야 합니다.") Integer documentsMatched) {
    }

    @Operation(summary = "정확도 실측 실행",
            description = "jar에 실은 정답셋과 배포 DB의 카드를 비교해 실측하고 결과를 저장한다. GET /metrics가 이 결과를 준다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/run")
    public MetricsResponse run() {
        return metricsService.run();
    }

    @Operation(summary = "사람 표시 저장",
            description = "대상 맞음/틀림과 서류 맞힌 개수를 저장한다. 같은 글 번호는 덮어쓴다. 표시한 뒤 카드가 바뀌면 다음 실측에서 빠지므로 다시 표시한다. "
                    + "정답셋에 없는 글 번호나 DB에 없는 공지는 404, BASIC 공지에 서류를 넣거나 정답 서류 수를 넘으면 400.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PutMapping(value = "/reviews/{articleNo}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void review(@PathVariable Long articleNo, @Valid @RequestBody ReviewRequest request) {
        metricsService.review(articleNo, request.targetCorrect(), request.documentsMatched());
    }
}
