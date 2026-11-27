package com.nf.noticeforecast.internal;

import com.nf.noticeforecast.config.OpenApiConfig;
import com.nf.noticeforecast.extract.ExtractionService;
import com.nf.noticeforecast.internal.dto.ReExtractResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "내부")
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/internal/notices", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalNoticeController {

    private final ExtractionService extractionService;

    @Operation(summary = "공지 하나 재추출", description = "상태와 관계없이 본문으로 AI 추출을 다시 하고 결과를 저장한다. 끝날 때까지 기다린다.",
            security = @SecurityRequirement(name = OpenApiConfig.ADMIN_KEY))
    @PostMapping("/{id}/re-extract")
    public ReExtractResponse reExtract(@PathVariable Long id) {
        return ReExtractResponse.from(extractionService.reExtract(id));
    }
}
