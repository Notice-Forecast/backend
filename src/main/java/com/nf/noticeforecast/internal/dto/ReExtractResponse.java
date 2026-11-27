package com.nf.noticeforecast.internal.dto;

import com.nf.noticeforecast.extract.CardExtractor;
import com.nf.noticeforecast.extract.card.Card;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ReExtractResponse(
        @Schema(description = "결과. DONE(카드 저장), NEEDS_ORIGINAL(검증 실패로 원문 확인 필요), RETRY_LATER(호출 실패, 상태 그대로)",
                example = "DONE") String result,
        @Schema(description = "응답한 모델", example = "claude-haiku-5-5", nullable = true) String model,
        @Schema(description = "AI 호출 횟수(검증 실패 시 1회 재시도)", example = "1") int attempts,
        @Schema(description = "검증 오류 또는 호출 오류") List<String> errors,
        @Schema(description = "저장한 카드", nullable = true) Card card
) {
    public static ReExtractResponse from(CardExtractor.Outcome outcome) {
        return switch (outcome) {
            case CardExtractor.Extracted e -> new ReExtractResponse("DONE", e.model(), e.attempts(), List.of(), e.card());
            case CardExtractor.Rejected r -> new ReExtractResponse("NEEDS_ORIGINAL", null, r.attempts(), r.errors(), null);
            case CardExtractor.RetryLater r -> new ReExtractResponse("RETRY_LATER", null, 0, List.of(r.message()), null);
        };
    }
}
