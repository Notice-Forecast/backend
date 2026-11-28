package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.CardDiff.CardChange;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 변경 요약 문구를 서버 규칙으로 만든다(11/28 결정: LLM 없이 항목별 문구, 여러 개면 이어 붙임).
 * 예: "신청 마감이 11/16 18:00에서 11/18 18:00로 바뀌었어요. 자격 조건이 바뀌었어요."
 */
public final class ChangeSummaries {

    private static final Map<String, String> SCHEDULE_LABELS = Map.of(
            "APPLY_START", "신청 시작",
            "APPLY_END", "신청 마감",
            "DOCUMENT", "서류 제출 일정",
            "EVENT", "행사 일정");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm");

    private ChangeSummaries() {
    }

    /** 변경이 없으면 null */
    public static String of(List<CardChange> changes) {
        if (changes.isEmpty()) {
            return null;
        }
        return changes.stream().map(ChangeSummaries::sentence).collect(Collectors.joining(" "));
    }

    static String sentence(CardChange change) {
        if (CardDiff.CONDITIONS.equals(change.field())) {
            return "자격 조건이 바뀌었어요.";
        }
        if (CardDiff.DOCUMENTS.equals(change.field())) {
            return "제출 서류가 바뀌었어요.";
        }
        String label = SCHEDULE_LABELS.getOrDefault(change.field(), "일정");
        if (change.before() == null) {
            return label + "이 " + dates(change.after()) + "로 추가됐어요.";
        }
        if (change.after() == null) {
            return label + "이 빠졌어요.";
        }
        return label + "이 " + dates(change.before()) + "에서 " + dates(change.after()) + "로 바뀌었어요.";
    }

    /** "2026-11-18T18:00:00+09:00" → "11/18 18:00", "2026-11-18" → "11/18". 여러 개면 쉼표로 잇는다. */
    static String dates(String value) {
        return Arrays.stream(value.split(", ")).map(ChangeSummaries::date).collect(Collectors.joining(", "));
    }

    private static String date(String at) {
        try {
            if (at.length() <= 10) {
                return LocalDate.parse(at).format(DATE);
            }
            return OffsetDateTime.parse(at).atZoneSameInstant(KoreanTime.ZONE).format(DATE_TIME);
        } catch (RuntimeException e) {
            return at;
        }
    }
}
