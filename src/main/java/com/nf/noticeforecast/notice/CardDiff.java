package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.global.time.KoreanTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 이전 카드와 새 카드를 비교해 바뀐 항목을 찾는다(CLAUDE.md 8.4).
 * <ul>
 *   <li>일정은 신청 시작·마감, 서류 제출, 행사 날짜를 종류(schedule.type)별로 비교한다. AI_SUGGESTED·RULE 날짜인
 *   준비 시작(PREP_START)은 변경으로 보지 않는다.</li>
 *   <li>자격 조건은 근거 문장과 순서를 빼고 field·op·values만 비교한다. FREE_TEXT·SELF_CHOICE는 개수만 비교한다.</li>
 *   <li>서류는 이름이 재추출마다 조금씩 달라질 수 있어 종류(type)와 개수만 비교한다.</li>
 * </ul>
 * AI 재추출로 표현만 달라진 것을 변경으로 알리지 않기 위해서다.
 */
public final class CardDiff {

    public static final String CONDITIONS = "CONDITIONS";
    public static final String DOCUMENTS = "DOCUMENTS";
    static final List<String> COMPARED_SCHEDULE_TYPES = List.of("APPLY_START", "APPLY_END", "DOCUMENT", "EVENT");
    private static final Set<String> COUNT_ONLY_FIELDS = Set.of("FREE_TEXT", "SELF_CHOICE");

    public record CardChange(String field, String before, String after) {
    }

    private CardDiff() {
    }

    /** 이전 카드가 없으면(첫 추출) 변경이 없다고 본다. */
    public static List<CardChange> diff(Card before, Card after) {
        if (before == null || after == null) {
            return List.of();
        }
        List<CardChange> changes = new ArrayList<>();
        for (String type : COMPARED_SCHEDULE_TYPES) {
            addIfChanged(changes, type, scheduleValue(before, type), scheduleValue(after, type));
        }
        addIfChanged(changes, CONDITIONS, conditionsValue(before), conditionsValue(after));
        addIfChanged(changes, DOCUMENTS, documentsValue(before), documentsValue(after));
        return changes;
    }

    private static void addIfChanged(List<CardChange> changes, String field, String before, String after) {
        if (!Objects.equals(before, after)) {
            changes.add(new CardChange(field, before, after));
        }
    }

    /**
     * 같은 종류가 여러 개면(서류 마감이 둘 등) 날짜를 정렬해 쉼표로 잇는다. 없으면 null.
     * 출처(source)로는 거르지 않는다. AI가 같은 마감에 출처만 다르게 붙여도 "마감이 빠졌어요"가 되지 않게 하려는 것이다.
     * AI 제안·규칙 날짜는 PREP_START뿐이고 PREP_START는 비교 대상 종류에 없다.
     */
    static String scheduleValue(Card card, String type) {
        List<String> values = card.schedule().stream()
                .filter(item -> type.equals(item.type()))
                .map(ScheduleItem::at)
                .filter(Objects::nonNull)
                .map(CardDiff::normalizeAt)
                .sorted()
                .toList();
        return values.isEmpty() ? null : String.join(", ", values);
    }

    /** 같은 시각을 +09:00·Z 등 다르게 적어도 같게 보이도록 한국 시간 +09:00 형식으로 맞춘다. 날짜만 있으면 그대로. */
    static String normalizeAt(String at) {
        String value = at.strip();
        if (value.length() <= 10) {
            return value;
        }
        try {
            return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    OffsetDateTime.parse(value).atZoneSameInstant(KoreanTime.ZONE).toOffsetDateTime());
        } catch (DateTimeParseException e) {
            return value;
        }
    }

    static String conditionsValue(Card card) {
        List<String> parts = new ArrayList<>();
        for (CardCondition condition : card.conditions()) {
            if (!COUNT_ONLY_FIELDS.contains(condition.field())) {
                parts.add(condition.field() + " " + condition.op() + " " + condition.values().stream()
                        .map(value -> "DEPARTMENT".equals(condition.field()) ? DepartmentNames.normalize(value) : value)
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
        }
        for (String field : COUNT_ONLY_FIELDS.stream().sorted().toList()) {
            long count = card.conditions().stream().filter(c -> field.equals(c.field())).count();
            if (count > 0) {
                parts.add(field + " x" + count);
            }
        }
        return parts.isEmpty() ? null : parts.stream().sorted().collect(Collectors.joining("; "));
    }

    static String documentsValue(Card card) {
        List<String> types = card.documents().stream()
                .map(CardDocument::type)
                .map(type -> type == null ? "OTHER" : type)
                .sorted()
                .toList();
        return types.isEmpty() ? null : String.join(", ", types);
    }
}
