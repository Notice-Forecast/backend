package com.nf.noticeforecast.metrics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "실측")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
public class MetricsController {

    private final MetricsService metricsService;

    @Operation(summary = "정확도 실측 결과",
            description = "마지막 실측 결과(추출·판정 정확도, 숨김 오류, 예보 적중률, 알림량)와 계산 시각, 표본 수. "
                    + "아직 못 잰 항목은 null이고, 실측을 한 번도 하지 않았으면 모든 항목이 null이다.")
    @GetMapping
    public MetricsResponse latest() {
        return metricsService.latest();
    }
}
