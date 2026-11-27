package com.nf.noticeforecast.extract.card;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI가 만든 카드를 저장 전에 검사한다(CLAUDE.md 8.3). 오류 문장은 재시도 때 AI에게 그대로 돌려준다.
 * 근거 문장은 공백만 정리한 뒤 출처 텍스트에 그대로 들어 있어야 한다. 지어낸 근거를 막는 핵심 검사다.
 */
public final class CardValidator {

    private static final Set<String> MANUAL_ONLY_FIELDS = Set.of("FREE_TEXT", "SELF_CHOICE");
    private static final Set<String> NUMBER_FIELDS = Set.of("GPA", "SEMESTER_COUNT");
    private static final Set<String> BOOLEAN_FIELDS = Set.of("DORM_RESIDENT", "INTERNATIONAL");
    private static final Set<String> GRADES = Set.of("1", "2", "3", "4", "5", "6");
    private static final double MAX_GPA = 4.5;

    private CardValidator() {
    }

    /**
     * @param bodyText        AI에게 보낸 본문(마스킹본)
     * @param attachmentTexts 첨부 파일 이름 → AI에게 보낸 첨부 텍스트
     */
    public static List<String> validate(Card card, String bodyText, Map<String, String> attachmentTexts) {
        List<String> errors = new ArrayList<>();
        if (card == null) {
            errors.add("카드가 비어 있습니다.");
            return errors;
        }
        if (blank(card.summary())) {
            errors.add("summary가 비어 있습니다.");
        }
        if (card.forStudents() == null) {
            errors.add("forStudents가 비어 있습니다.");
        }
        for (int i = 0; i < card.conditions().size(); i++) {
            validateCondition("conditions[" + i + "]", card.conditions().get(i), bodyText, attachmentTexts, errors);
        }
        long applyEnds = 0;
        for (int i = 0; i < card.schedule().size(); i++) {
            ScheduleItem item = card.schedule().get(i);
            validateSchedule("schedule[" + i + "]", item, errors);
            if (item != null && "APPLY_END".equals(item.type())) {
                applyEnds++;
            }
        }
        if (applyEnds > 1) {
            errors.add("APPLY_END는 하나만 넣어야 합니다. 신청 마감이 여러 개면 학생이 신청을 끝내야 하는 마지막 날을 APPLY_END로, 나머지는 DOCUMENT나 EVENT로 넣으세요.");
        }
        for (int i = 0; i < card.documents().size(); i++) {
            CardDocument document = card.documents().get(i);
            String path = "documents[" + i + "]";
            if (document == null || blank(document.name())) {
                errors.add(path + ".name이 비어 있습니다.");
            } else if (!CardEnums.DOCUMENT_TYPES.contains(document.type())) {
                errors.add(path + ".type '" + document.type() + "'은 허용된 값이 아닙니다.");
            }
        }
        return errors;
    }

    private static void validateCondition(String path, CardCondition condition, String bodyText,
                                          Map<String, String> attachmentTexts, List<String> errors) {
        if (condition == null) {
            errors.add(path + "가 비어 있습니다.");
            return;
        }
        String field = condition.field();
        String op = condition.op();
        List<String> values = condition.values();
        if (!CardEnums.CONDITION_FIELDS.contains(field)) {
            errors.add(path + ".field '" + field + "'은 허용된 값이 아닙니다.");
            return;
        }
        if (!CardEnums.CONDITION_OPS.contains(op)) {
            errors.add(path + ".op '" + op + "'은 허용된 값이 아닙니다.");
            return;
        }
        if (MANUAL_ONLY_FIELDS.contains(field)) {
            if (!"MANUAL".equals(op) || !values.isEmpty()) {
                errors.add(path + ": " + field + "는 op MANUAL, values []여야 합니다.");
            }
        } else if ("MANUAL".equals(op)) {
            errors.add(path + ": MANUAL은 FREE_TEXT·SELF_CHOICE에만 씁니다. 판단이 안 되는 조건은 FREE_TEXT로 넣으세요.");
        } else if (NUMBER_FIELDS.contains(field)) {
            if (!("GTE".equals(op) || "LTE".equals(op)) || values.size() != 1 || !number(values.getFirst())) {
                errors.add(path + ": " + field + "는 op GTE 또는 LTE와 숫자 값 하나여야 합니다.");
            } else if ("GPA".equals(field) && Double.parseDouble(values.getFirst()) > MAX_GPA) {
                errors.add(path + ": GPA는 4.5 만점 평점입니다. 백분위나 100점 만점 점수는 GPA로 바꾸지 말고 FREE_TEXT로 넣으세요.");
            }
        } else if (BOOLEAN_FIELDS.contains(field)) {
            if (!"IN".equals(op) || values.size() != 1 || !Set.of("TRUE", "FALSE").contains(values.getFirst())) {
                errors.add(path + ": " + field + "는 op IN과 values [\"TRUE\"] 또는 [\"FALSE\"]여야 합니다.");
            }
        } else if (!("IN".equals(op) || "NOT_IN".equals(op)) || values.isEmpty()) {
            errors.add(path + ": " + field + "는 op IN 또는 NOT_IN과 값이 하나 이상 있어야 합니다.");
        } else if ("GRADE".equals(field) && !GRADES.containsAll(values)) {
            errors.add(path + ": GRADE 값은 \"1\"~\"6\"이어야 합니다.");
        } else if ("ENROLLMENT_STATUS".equals(field) && !CardEnums.ENROLLMENT_VALUES.containsAll(values)) {
            errors.add(path + ": ENROLLMENT_STATUS 값은 " + CardEnums.ENROLLMENT_VALUES + " 중에서 써야 합니다.");
        } else if ("DEPARTMENT".equals(field) && values.stream().anyMatch(CardValidator::blank)) {
            errors.add(path + ": DEPARTMENT 값이 비어 있습니다.");
        }
        validateEvidence(path, condition, bodyText, attachmentTexts, errors);
    }

    private static void validateEvidence(String path, CardCondition condition, String bodyText,
                                         Map<String, String> attachmentTexts, List<String> errors) {
        if (blank(condition.evidence())) {
            errors.add(path + ".evidence가 비어 있습니다.");
            return;
        }
        String source = condition.evidenceSource();
        String text;
        if (CardEnums.BODY_SOURCE.equals(source)) {
            text = bodyText;
        } else if (source != null && source.startsWith(CardEnums.ATTACHMENT_SOURCE_PREFIX)) {
            text = attachmentTexts.get(source.substring(CardEnums.ATTACHMENT_SOURCE_PREFIX.length()));
            if (text == null) {
                errors.add(path + ".evidenceSource '" + source + "'은 받은 첨부 이름과 다릅니다.");
                return;
            }
        } else {
            errors.add(path + ".evidenceSource는 '본문' 또는 '첨부: 파일명'이어야 합니다.");
            return;
        }
        if (text == null || !normalize(text).contains(normalize(condition.evidence()))) {
            errors.add(path + ".evidence '" + condition.evidence() + "'가 " + source
                    + "에 그대로 있지 않습니다. 원문 문장을 고치지 말고 그대로 인용하세요.");
        }
    }

    private static void validateSchedule(String path, ScheduleItem item, List<String> errors) {
        if (item == null) {
            errors.add(path + "가 비어 있습니다.");
            return;
        }
        if (!CardEnums.SCHEDULE_TYPES.contains(item.type())) {
            errors.add(path + ".type '" + item.type() + "'은 허용된 값이 아닙니다.");
        }
        if (!CardEnums.AI_SCHEDULE_SOURCES.contains(item.source())) {
            errors.add(path + ".source는 NOTICE 또는 AI_SUGGESTED여야 합니다.");
        }
        if (blank(item.label())) {
            errors.add(path + ".label이 비어 있습니다.");
        }
        if (item.allDay() == null || item.at() == null) {
            errors.add(path + ": at과 allDay가 필요합니다.");
            return;
        }
        try {
            if (item.allDay()) {
                LocalDate.parse(item.at());
            } else {
                OffsetDateTime.parse(item.at());
            }
        } catch (DateTimeParseException e) {
            errors.add(path + ".at '" + item.at() + "' 형식이 틀렸습니다. allDay true면 YYYY-MM-DD, false면 YYYY-MM-DDTHH:mm:ss+09:00입니다.");
        }
    }

    static String normalize(String text) {
        return text.replaceAll("\\s+", " ").strip();
    }

    private static boolean number(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
