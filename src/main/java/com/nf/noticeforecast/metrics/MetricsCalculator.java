package com.nf.noticeforecast.metrics;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.DepartmentNames;
import com.nf.noticeforecast.notice.ExtractionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 정답셋과 배포 DB 카드를 비교한다(CLAUDE.md 15.2, 항목별 기준은 11/27·11/28 결정). DB를 모르는 순수 계산이다.
 * <ul>
 *   <li>원문 확인 필요 공지와 카드가 없는 공지(DB에 없음·추출 전·실패)는 표본에서 빼고 따로 센다.</li>
 *   <li>마감일: 날짜가 같고, 정답에 시각이 있으면 AI도 시각이 있고 같아야 한다. 정답이 없으면 AI도 없어야 맞다.</li>
 *   <li>대상·서류: 사람이 표시한 것만 센다. 표시한 뒤 카드가 바뀌었으면(revision이 다르면) 뺀다.</li>
 *   <li>자격 조건: 정답 조건 중 AI가 field·op·값까지 똑같이 찾은 비율. FREE_TEXT는 있는지만 보고, SELF_CHOICE는 뺀다.</li>
 *   <li>판정: 페르소나별 결과가 정확히 같으면 맞다. 판정과 조건·서류는 FULL 공지만 잰다.</li>
 * </ul>
 */
public final class MetricsCalculator {

    private static final String FREE_TEXT = "FREE_TEXT";
    private static final Set<String> NUMBER_FIELDS = Set.of("GRADE", "GPA", "SEMESTER_COUNT");

    private MetricsCalculator() {
    }

    /** 정답셋 공지 하나의 배포 DB 상태. DB에 없으면 status가 null이다. */
    public record Sample(EvalLabel label, ExtractionStatus status, Card card, Instant deadlineAt, boolean deadlineAllDay,
                         int revision) {

        boolean scored() {
            return status == ExtractionStatus.DONE && card != null;
        }
    }

    public record Review(Boolean targetCorrect, Integer documentsMatched, int revision) {
    }

    public static MetricsReport compute(String model, List<Sample> samples, Map<String, Persona> personas,
                                        Map<Long, Review> reviews, List<MetricsReport.AlertVolume> alerts) {
        int full = 0;
        int basic = 0;
        int needsOriginal = 0;
        int missing = 0;
        Tally deadline = new Tally();
        Tally target = new Tally();
        Tally conditions = new Tally();
        Tally documents = new Tally();
        Tally verdict = new Tally();
        Tally hidden = new Tally();
        Map<String, Persona> ordered = new TreeMap<>(personas);
        for (Sample sample : samples) {
            if (sample.status() == ExtractionStatus.NEEDS_ORIGINAL) {
                needsOriginal++;
                continue;
            }
            if (!sample.scored()) {
                missing++;
                continue;
            }
            EvalLabel label = sample.label();
            if (label.full()) {
                full++;
            } else {
                basic++;
            }
            deadline.add(deadlineMatches(label.deadline(), sample.deadlineAt(), sample.deadlineAllDay()));
            Review review = reviews.get(label.articleNo());
            boolean reviewValid = review != null && review.revision() == sample.revision();
            if (reviewValid && review.targetCorrect() != null) {
                target.add(review.targetCorrect());
            }
            if (!label.full()) {
                continue;
            }
            List<String> expected = conditionKeys(label.conditions().stream()
                    .map(c -> new Key(c.field(), c.op(), c.values())).toList());
            Set<String> found = new HashSet<>(conditionKeys(sample.card().conditions().stream()
                    .map(c -> new Key(c.field(), c.op(), c.values())).toList()));
            conditions.correct += (int) expected.stream().filter(found::contains).count();
            conditions.total += expected.size();
            if (reviewValid && review.documentsMatched() != null) {
                documents.correct += Math.min(review.documentsMatched(), label.documents().size());
                documents.total += label.documents().size();
            }
            for (Map.Entry<String, String> entry : new TreeMap<>(label.verdicts()).entrySet()) {
                Persona persona = ordered.get(entry.getKey());
                if (persona == null) {
                    continue;
                }
                Verdict human = Verdict.valueOf(entry.getValue());
                Verdict service = ProfileJudge.judge(sample.card(), persona);
                verdict.add(human == service);
                if (human != Verdict.HIDDEN) {
                    hidden.total++;
                    if (service == Verdict.HIDDEN) {
                        hidden.correct++;
                    }
                }
            }
        }
        return new MetricsReport(model,
                new MetricsReport.Samples(full, basic, needsOriginal, missing),
                new MetricsReport.Extraction(deadline.score(), target.score(), conditions.score(), documents.score()),
                verdict.score(),
                MetricsReport.HiddenRate.of(hidden.correct, hidden.total),
                null,
                alerts);
    }

    static boolean deadlineMatches(String expected, Instant actual, boolean actualAllDay) {
        if (expected == null || expected.isBlank()) {
            return actual == null;
        }
        if (actual == null) {
            return false;
        }
        try {
            if (expected.length() == 10) {
                return LocalDate.parse(expected).equals(LocalDate.ofInstant(actual, KoreanTime.ZONE));
            }
            return !actualAllDay && OffsetDateTime.parse(expected).toInstant().equals(actual);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    record Key(String field, String op, List<String> values) {
    }

    /** 조건을 비교용 문자열로 바꾼다. SELF_CHOICE는 빼고, FREE_TEXT는 몇 개든 하나로 친다. */
    static List<String> conditionKeys(List<Key> conditions) {
        List<String> keys = new ArrayList<>();
        boolean freeText = false;
        for (Key condition : conditions) {
            String field = condition.field();
            String op = condition.op();
            if ("SELF_CHOICE".equals(field)) {
                continue;
            }
            if (FREE_TEXT.equals(field)) {
                freeText = true;
                continue;
            }
            String normalized = condition.values().stream()
                    .map(value -> normalizeValue(field, value))
                    .sorted()
                    .distinct()
                    .collect(Collectors.joining(","));
            keys.add(field + "|" + op + "|" + normalized);
        }
        if (freeText) {
            keys.add(FREE_TEXT);
        }
        return keys;
    }

    private static String normalizeValue(String field, String value) {
        if (value == null) {
            return "";
        }
        if ("DEPARTMENT".equals(field)) {
            return DepartmentNames.normalize(value);
        }
        if (NUMBER_FIELDS.contains(field)) {
            BigDecimal number = ProfileJudge.decimal(value);
            return number == null ? value.strip() : number.stripTrailingZeros().toPlainString();
        }
        return value.strip().toUpperCase();
    }

    private static final class Tally {

        int correct;
        int total;

        void add(boolean ok) {
            total++;
            if (ok) {
                correct++;
            }
        }

        MetricsReport.Score score() {
            return MetricsReport.Score.of(correct, total);
        }
    }
}
