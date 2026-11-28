package com.nf.noticeforecast.extract;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Anthropic Messages API를 RestClient로 직접 부른다(CLAUDE.md 3장, 8.3). */
@Slf4j
@Component
public class AnthropicLlmClient implements LlmClient {

    private static final String BASE_URL = "https://api.anthropic.com";
    private static final String API_VERSION = "2023-06-01";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    private final LlmProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient restClient;

    public AnthropicLlmClient(LlmProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        String response;
        try {
            response = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", properties.apiKey())
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(request))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String body = e.getResponseBodyAsString();
            String message = "LLM returned " + status + ": " + body;
            if (status == 429 || status >= 500) {
                throw new LlmUnavailableException(message, e);
            }
            throw new LlmRejectedException(message, e, status, isInputTooLong(status, body));
        } catch (ResourceAccessException e) {
            throw new LlmUnavailableException("LLM unreachable: " + e.getMessage(), e);
        }
        return parse(response);
    }

    static boolean isInputTooLong(int status, String body) {
        if (status == 413) {
            return true;
        }
        if (status != 400 || body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("prompt is too long")
                || lower.contains("input is too long")
                || lower.contains("too many tokens")
                || lower.contains("context length")
                || lower.contains("maximum context");
    }

    Map<String, Object> requestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("max_tokens", properties.maxTokens());
        body.put("system", request.system());
        body.put("messages", List.of(Map.of("role", "user", "content", request.user())));
        body.put("output_config", Map.of(
                "effort", properties.effort(),
                "format", Map.of("type", "json_schema", "schema", request.jsonSchema())));
        return body;
    }

    LlmResult parse(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmUnavailableException("LLM returned empty response", null);
        }
        JsonNode root;
        try {
            root = jsonMapper.readTree(response);
        } catch (JacksonException e) {
            throw new LlmUnavailableException("LLM response unreadable: " + e.getOriginalMessage(), e);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asString())) {
                text.append(block.path("text").asString());
            }
        }
        JsonNode usage = root.path("usage");
        return new LlmResult(text.toString(), root.path("stop_reason").asString(), root.path("model").asString(),
                usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong());
    }
}
