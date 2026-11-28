package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.forecast.SeriesAdminService;
import com.nf.noticeforecast.forecast.SeriesAnchor;
import com.nf.noticeforecast.forecast.SeriesLinker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/series", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalSeriesController {

    private final SeriesLinker seriesLinker;
    private final SeriesAdminService seriesAdminService;

    public record LinkStartResponse(
            @Schema(description = "이번 요청으로 새로 시작했으면 true", example = "true") boolean started,
            @Schema(description = "지금 실행 중이면 true", example = "true") boolean running) {
    }

    public record MergeRequest(
            @Schema(description = "이 계열로 합칠(지울) 계열 id", example = "89") @NotNull(message = "필수 값입니다.") Long from) {
    }

    public record MergeResponse(@Schema(description = "옮긴 공지 수", example = "15") int moved) {
    }

    public record CurateRequest(
            @Schema(description = "계열 이름. 비우면 그대로", example = "복수전공·부전공 신청 안내") String name,
            @Schema(description = "예보 기준점. TERM_START(학기 시작) 또는 TERM_END(학기 끝). null이면 지움", example = "TERM_END")
            SeriesAnchor anchor,
            @Schema(description = "시드로 쓸지", example = "true") @NotNull(message = "필수 값입니다.") Boolean seed) {
    }

    @Operation(summary = "계열 판별 일괄 실행",
            description = "계열이 없는 공지(과거 이력 포함)를 오래된 글부터 계열에 붙인다. 오래 걸려서 백그라운드로 실행하고 바로 202를 돌려준다. "
                    + "진행과 결과는 서버 로그 'series linking'에서 본다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LinkStartResponse link() {
        boolean started = seriesLinker.startInBackground();
        return new LinkStartResponse(started, true);
    }

    @Operation(summary = "계열 목록", description = "회차가 minRounds 이상인 계열을 회차 많은 순으로. 시드 고르기용.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @GetMapping
    public List<SeriesAdminService.SeriesSummary> list(@RequestParam(defaultValue = "3") long minRounds) {
        return seriesAdminService.list(minRounds);
    }

    @Operation(summary = "계열 회차", description = "회차별 게시일·제목·학기와 학기 시작·끝에서 며칠인지. 기준점을 고를 때 본다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @GetMapping("/{id}")
    public SeriesAdminService.SeriesDetail detail(@PathVariable Long id) {
        return seriesAdminService.detail(id);
    }

    @Operation(summary = "계열 합치기", description = "from 계열의 공지를 이 계열로 옮기고 from 계열을 지운다. 잘못 나뉜 계열을 고칠 때 쓴다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/{id}/merge")
    public MergeResponse merge(@PathVariable Long id, @Valid @RequestBody MergeRequest request) {
        return new MergeResponse(seriesAdminService.merge(id, request.from()));
    }

    @Operation(summary = "시드 지정", description = "계열에 이름과 기준점을 붙이고 시드로 지정하거나 해제한다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PutMapping("/{id}")
    public SeriesAdminService.SeriesSummary curate(@PathVariable Long id, @Valid @RequestBody CurateRequest request) {
        return seriesAdminService.curate(id, request.name(), request.anchor(), request.seed());
    }
}
