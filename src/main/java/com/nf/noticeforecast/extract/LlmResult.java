package com.nf.noticeforecast.extract;

/** @param model 실제로 응답한 모델 id */
public record LlmResult(String text, String stopReason, String model, long inputTokens, long outputTokens) {
}
