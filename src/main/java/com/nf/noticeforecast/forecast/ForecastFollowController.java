package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.device.CurrentDevice;
import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.forecast.dto.ForecastFollowRequest;
import com.nf.noticeforecast.forecast.dto.ForecastFollowResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "예보")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/devices/me/forecast-follows", produces = MediaType.APPLICATION_JSON_VALUE)
public class ForecastFollowController {

    private final ForecastService forecastService;

    @Operation(summary = "예보 계열 구독·알림 방식 변경",
            description = "같은 계열을 다시 보내면 notify를 덮어씁니다.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @PutMapping("/{seriesId}")
    public ForecastFollowResponse follow(@CurrentDevice Device device, @PathVariable Long seriesId,
                                         @Valid @RequestBody ForecastFollowRequest request) {
        return forecastService.follow(device, seriesId, request.getNotify(), request.getSource());
    }

    @Operation(summary = "예보 계열 구독 취소",
            description = "구독이 없어도 204를 돌려줍니다.",
            security = @SecurityRequirement(name = OpenApiConfig.DEVICE_ID))
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{seriesId}")
    public void unfollow(@CurrentDevice Device device, @PathVariable Long seriesId) {
        forecastService.unfollow(device.getId(), seriesId);
    }
}
