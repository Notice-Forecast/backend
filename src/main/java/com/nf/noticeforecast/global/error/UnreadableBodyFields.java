package com.nf.noticeforecast.global.error;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

final class UnreadableBodyFields {

    private UnreadableBodyFields() {
    }

    static Map<String, String> from(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof JacksonException jackson) {
                String path = pathOf(jackson.getPath());
                return path.isEmpty() ? Map.of() : Map.of(path, reasonOf(jackson));
            }
        }
        return Map.of();
    }

    private static String pathOf(List<JacksonException.Reference> references) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : references) {
            if (reference.getPropertyName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
            }
        }
        return path.toString();
    }

    private static String reasonOf(JacksonException ex) {
        if (ex instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                && invalid.getTargetType().isEnum()) {
            String allowed = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return allowed + " 중 하나여야 합니다.";
        }
        return "형식이 올바르지 않습니다.";
    }
}
