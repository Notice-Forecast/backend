package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.CurrentDevice;
import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.forecast.dto.ForecastDetailResponse;
import com.nf.noticeforecast.forecast.dto.ForecastListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예보")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/forecasts", produces = MediaType.APPLICATION_JSON_VALUE)
public class ForecastController {

    private final ForecastService forecastService;

    @Operation(summary = "예보 목록(구독 중·추천)",
            description = "시드 계열을 대상으로 구독 중인 목록과 추천 목록을 돌려줍니다. q를 보내면 추천 여부와 무관하게 이름으로 검색합니다.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @GetMapping
    public ForecastListResponse list(
            @CurrentDevice Device device,
            @Parameter(description = "계열 이름 검색") @RequestParam(required = false) String q,
            @Parameter(description = "관심 영역(쉼표 구분, 공지 분류 값)", example = "ACADEMIC,SCHOLARSHIP")
            @RequestParam(required = false) String interests) {
        return forecastService.list(device, q, interests);
    }

    @Operation(summary = "예보 상세",
            description = "예측 기간, 신뢰도, 근거 문장, 지난 회차, 미리 준비할 서류, 내 구독 상태를 돌려줍니다.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @GetMapping("/{seriesId}")
    public ForecastDetailResponse detail(@CurrentDevice(required = false) Device device,
                                         @PathVariable Long seriesId) {
        return forecastService.detail(device, seriesId);
    }
}
