package com.nf.noticeforecast.extract.card;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 구조화 출력(output_config.format)에 넣는 카드 JSON 스키마. 모든 객체는 additionalProperties=false가 필요하다. */
public final class CardSchema {

    private static final Map<String, Object> SCHEMA = build();

    private CardSchema() {
    }

    public static Map<String, Object> schema() {
        return SCHEMA;
    }

    private static Map<String, Object> build() {
        Map<String, Object> condition = object(Map.of(
                "field", enumOf(CardEnums.CONDITION_FIELDS),
                "op", enumOf(CardEnums.CONDITION_OPS),
                "values", Map.of("type", "array", "items", Map.of("type", "string")),
                "evidence", Map.of("type", "string"),
                "evidenceSource", Map.of("type", "string")),
                List.of("field", "op", "values", "evidence", "evidenceSource"));

        Map<String, Object> scheduleItem = object(Map.of(
                "type", enumOf(CardEnums.SCHEDULE_TYPES),
                "label", Map.of("type", "string"),
                "at", Map.of("type", "string"),
                "allDay", Map.of("type", "boolean"),
                "source", enumOf(CardEnums.AI_SCHEDULE_SOURCES)),
                List.of("type", "label", "at", "allDay", "source"));

        Map<String, Object> document = object(Map.of(
                "name", Map.of("type", "string"),
                "type", enumOf(CardEnums.DOCUMENT_TYPES),
                "note", Map.of("type", List.of("string", "null"))),
                List.of("name", "type", "note"));

        return object(Map.of(
                "summary", Map.of("type", "string"),
                "forStudents", Map.of("type", "boolean"),
                "conditions", Map.of("type", "array", "items", condition),
                "schedule", Map.of("type", "array", "items", scheduleItem),
                "documents", Map.of("type", "array", "items", document),
                "howToApply", Map.of("type", List.of("string", "null"))),
                List.of("summary", "forStudents", "conditions", "schedule", "documents", "howToApply"));
    }

    private static Map<String, Object> object(Map<String, Object> properties, List<String> order) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        order.forEach(key -> ordered.put(key, properties.get(key)));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", ordered);
        schema.put("required", order);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> enumOf(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }
}
