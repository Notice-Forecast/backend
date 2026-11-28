package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.forecast.SeriesLinker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/series", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalSeriesController {

    private final SeriesLinker seriesLinker;

    public record LinkStartResponse(
            @Schema(description = "이번 요청으로 새로 시작했으면 true", example = "true") boolean started,
            @Schema(description = "지금 실행 중이면 true", example = "true") boolean running) {
    }

    @Operation(summary = "계열 판별 일괄 실행",
            description = "계열이 없는 공지(과거 이력 포함)를 오래된 글부터 계열에 붙인다. 오래 걸려서 백그라운드로 실행하고 바로 202를 돌려준다. "
                    + "진행과 결과는 서버 로그 'series linking finished'에서 본다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LinkStartResponse link() {
        boolean started = seriesLinker.startInBackground();
        return new LinkStartResponse(started, true);
    }
}
