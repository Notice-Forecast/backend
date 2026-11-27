package com.nf.noticeforecast.extract.card;

import java.util.List;

/** 카드 JSON에 쓰는 열거형 값(CLAUDE.md 6.2). 스키마와 검증이 같은 목록을 쓴다. */
public final class CardEnums {

    public static final List<String> CONDITION_FIELDS = List.of("GRADE", "DEPARTMENT", "ENROLLMENT_STATUS", "GPA",
            "SEMESTER_COUNT", "DORM_RESIDENT", "INTERNATIONAL", "FREE_TEXT", "SELF_CHOICE");
    public static final List<String> CONDITION_OPS = List.of("IN", "NOT_IN", "GTE", "LTE", "MANUAL");
    public static final List<String> ENROLLMENT_VALUES = List.of("ENROLLED", "LEAVE", "RETURNING",
            "GRADUATE_STUDENT", "COMPLETED");
    public static final List<String> SCHEDULE_TYPES = List.of("PREP_START", "APPLY_START", "APPLY_END", "DOCUMENT",
            "EVENT");
    /** RULE은 서버가 서류 소요일 표로 넣는 값이라 AI에게는 주지 않는다. */
    public static final List<String> AI_SCHEDULE_SOURCES = List.of("NOTICE", "AI_SUGGESTED");
    public static final List<String> DOCUMENT_TYPES = List.of("RECOMMENDATION", "CERTIFICATE", "SELF_INTRODUCTION",
            "APPLICATION_FORM", "OTHER");
    public static final String BODY_SOURCE = "본문";
    public static final String ATTACHMENT_SOURCE_PREFIX = "첨부: ";

    private CardEnums() {
    }
}
