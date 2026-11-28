package com.nf.noticeforecast.metrics;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 정확도 실측 결과(CLAUDE.md 15.2, 11/28 결정). 아직 못 잰 항목은 null이다. DB에는 이 모양 그대로 JSON으로 저장한다. */
public record MetricsReport(
        @Schema(description = "실측한 추출 모델", example = "claude-haiku-5-5") String model,
        Samples samples,
        Extraction extraction,
        @Schema(description = "판정 정확도. 페르소나 3명 × FULL 공지. 못 쟀으면 null", nullable = true) Score verdict,
        @Schema(description = "사람 판정이 해당·조건부인 (페르소나, 공지) 쌍 중 서비스가 숨긴 비율. 못 쟀으면 null", nullable = true)
        HiddenRate hiddenWhileEligible,
        @Schema(description = "예보 적중률. 백테스트를 구현하기 전에는 null", nullable = true) Forecast forecast,
        @Schema(description = "페르소나별 새 공지 알림량") List<AlertVolume> alerts) {

    public record Samples(
            @Schema(description = "표본에 든 FULL 공지 수", example = "10") int full,
            @Schema(description = "표본에 든 BASIC 공지 수", example = "10") int basic,
            @Schema(description = "AI가 원문 확인 필요로 둔 정답셋 공지 수(표본에서 뺌)", example = "1") int needsOriginal,
            @Schema(description = "배포 DB에 없거나 아직 카드가 없는 정답셋 공지 수(표본에서 뺌)", example = "0") int missing) {
    }

    public record Extraction(
            @Schema(description = "마감일. 자동. 못 쟀으면 null", nullable = true) Score deadline,
            @Schema(description = "대상. 사람이 표시한 공지만. 표시가 없으면 null", nullable = true) Score target,
            @Schema(description = "자격 조건. 정답 조건 중 AI가 찾은 비율(FULL). 못 쟀으면 null", nullable = true) Score conditions,
            @Schema(description = "서류. 사람이 표시한 맞힌 개수(FULL). 표시가 없으면 null", nullable = true) Score documents) {
    }

    public record Score(
            @Schema(example = "18") int correct,
            @Schema(example = "19") int total,
            @Schema(example = "0.947") double rate) {

        static Score of(int correct, int total) {
            return total == 0 ? null : new Score(correct, total, round(correct, total));
        }
    }

    public record HiddenRate(
            @Schema(example = "0") int count,
            @Schema(example = "14") int denominator,
            @Schema(example = "0.0") double rate) {

        static HiddenRate of(int count, int denominator) {
            return denominator == 0 ? null : new HiddenRate(count, denominator, round(count, denominator));
        }
    }

    public record Forecast(
            @Schema(example = "8") int hit,
            @Schema(example = "11") int total,
            @Schema(example = "0.727") double rate,
            @Schema(description = "실제 게시일과 예측 중심의 차이 절댓값 평균(일)", example = "3.4", nullable = true) Double meanErrorDays) {
    }

    public record AlertVolume(
            @Schema(example = "P1") String persona,
            @Schema(description = "서버 판정으로 새 공지 알림을 받을 공지 수", example = "42") int notified,
            @Schema(description = "최근 30일 새 공지(카드 있는 것) 수", example = "260") int total,
            @Schema(description = "1 - notified/total. total이 0이면 null", example = "0.838", nullable = true) Double reduction) {
    }

    static double round(int numerator, int denominator) {
        return Math.round(numerator * 1000.0 / denominator) / 1000.0;
    }
}
