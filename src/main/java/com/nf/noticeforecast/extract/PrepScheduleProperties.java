package com.nf.noticeforecast.extract;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서류 종류별 기본 소요일(CLAUDE.md 8.3 추천 일정 규칙). OTHER는 표 값 없이 AI 제안만 쓰므로 넣지 않는다(11/26 결정).
 *
 * @param leadDays          서류 종류(documents[].type) → 마감 며칠 전부터 준비할지
 * @param noDocumentDays    서류가 없을 때 마감 며칠 전을 준비 시작으로 둘지
 */
@ConfigurationProperties(prefix = "app.prep")
public record PrepScheduleProperties(Map<String, Integer> leadDays, Integer noDocumentDays) {

    public PrepScheduleProperties {
        leadDays = leadDays == null || leadDays.isEmpty()
                ? Map.of("RECOMMENDATION", 7, "CERTIFICATE", 1, "SELF_INTRODUCTION", 3, "APPLICATION_FORM", 1)
                : Map.copyOf(leadDays);
        noDocumentDays = noDocumentDays == null ? 3 : noDocumentDays;
    }

    public static PrepScheduleProperties defaults() {
        return new PrepScheduleProperties(null, null);
    }
}
