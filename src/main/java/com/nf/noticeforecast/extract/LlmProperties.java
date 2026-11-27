package com.nf.noticeforecast.extract;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(String apiKey, String model, String effort, Integer maxTokens) {

    public LlmProperties {
        model = model == null || model.isBlank() ? "claude-haiku-5-5" : model;
        effort = effort == null || effort.isBlank() ? "medium" : effort;
        maxTokens = maxTokens == null ? 16_000 : maxTokens;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
