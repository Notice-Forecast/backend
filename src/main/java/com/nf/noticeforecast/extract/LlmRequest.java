package com.nf.noticeforecast.extract;

import java.util.Map;

public record LlmRequest(String system, String user, Map<String, Object> jsonSchema) {
}
