package com.nf.noticeforecast.metrics;

import static com.nf.noticeforecast.metrics.ProfileJudgeTest.card;
import static com.nf.noticeforecast.metrics.ProfileJudgeTest.cond;
import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.notice.ExtractionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsCalculatorTest {

    private static final Map<String, Persona> PERSONAS = Map.of(
            "P1", new Persona("컴퓨터정보공학부", List.of(), 3, "ENROLLED", 3.8, false, false),
            "P3", new Persona("경영학과", List.of(), 4, "LEAVE", 2.8, false, true));

    @Test
    void deadlineRules() {
        Instant evening = Instant.parse("2026-11-21T08:00:00Z");
        Instant midnightKst = Instant.parse("2026-11-20T15:00:00Z");

        assertThat(MetricsCalculator.deadlineMatches(null, null, false)).isTrue();
        assertThat(MetricsCalculator.deadlineMatches(null, evening, false)).isFalse();
        assertThat(MetricsCalculator.deadlineMatches("2026-11-21", evening, false)).isTrue();
        assertThat(MetricsCalculator.deadlineMatches("2026-11-21", midnightKst, true)).isTrue();
        assertThat(MetricsCalculator.deadlineMatches("2026-11-21T17:00:00+09:00", evening, false)).isTrue();
        assertThat(MetricsCalculator.deadlineMatches("2026-11-21T17:00:00+09:00", Instant.parse("2026-11-21T09:00:00Z"),
                false)).isFalse();
        assertThat(MetricsCalculator.deadlineMatches("2026-11-21T00:00:00+09:00", midnightKst, true)).isFalse();
    }

    @Test
    void conditionKeysNormalizeAndCollapseFreeText() {
        List<String> keys = MetricsCalculator.conditionKeys(List.of(
                new MetricsCalculator.Key("GPA", "GTE", List.of("3.0")),
                new MetricsCalculator.Key("DEPARTMENT", "IN", List.of("공간디자인ㆍ소비자학과")),
                new MetricsCalculator.Key("FREE_TEXT", "MANUAL", List.of()),
                new MetricsCalculator.Key("FREE_TEXT", "MANUAL", List.of()),
                new MetricsCalculator.Key("SELF_CHOICE", "MANUAL", List.of())));

        assertThat(keys).containsExactly("GPA|GTE|3", "DEPARTMENT|IN|공간디자인소비자학과", "FREE_TEXT");
    }

    @Test
    void scoresFullNoticeAndSkipsUnscoredSamples() {
        EvalLabel full = new EvalLabel("가톨릭대학교", 1L, "A", "FULL", "2026-11-21", "재학생",
                List.of(new EvalLabel.Condition("ENROLLMENT_STATUS", "IN", List.of("ENROLLED")),
                        new EvalLabel.Condition("GPA", "GTE", List.of("3.0")),
                        new EvalLabel.Condition("FREE_TEXT", "MANUAL", List.of())),
                List.of("추천서", "재학증명서"), Map.of("P1", "CONDITIONAL", "P3", "HIDDEN"));
        Card card = card(true, cond("ENROLLMENT_STATUS", "IN", "ENROLLED"), cond("GPA", "GTE", "2.5"),
                cond("FREE_TEXT", "MANUAL"));
        EvalLabel roster = new EvalLabel("가톨릭대학교", 2L, "A", "BASIC", null, "x", null, null, null);
        EvalLabel absent = new EvalLabel("가톨릭대학교", 3L, "A", "BASIC", null, "x", null, null, null);
        List<MetricsCalculator.Sample> samples = List.of(
                new MetricsCalculator.Sample(full, ExtractionStatus.DONE, card, Instant.parse("2026-11-21T08:00:00Z"),
                        false, 2),
                new MetricsCalculator.Sample(roster, ExtractionStatus.NEEDS_ORIGINAL, null, null, false, 0),
                new MetricsCalculator.Sample(absent, null, null, null, false, 0));
        Map<Long, MetricsCalculator.Review> reviews = Map.of(1L, new MetricsCalculator.Review(true, 1, 2));

        MetricsReport report = MetricsCalculator.compute("model", samples, PERSONAS, reviews, List.of());

        assertThat(report.samples()).isEqualTo(new MetricsReport.Samples(1, 0, 1, 1));
        assertThat(report.extraction().deadline()).isEqualTo(new MetricsReport.Score(1, 1, 1.0));
        assertThat(report.extraction().target()).isEqualTo(new MetricsReport.Score(1, 1, 1.0));
        assertThat(report.extraction().conditions()).isEqualTo(new MetricsReport.Score(2, 3, 0.667));
        assertThat(report.extraction().documents()).isEqualTo(new MetricsReport.Score(1, 2, 0.5));
        assertThat(report.verdict()).isEqualTo(new MetricsReport.Score(2, 2, 1.0));
        assertThat(report.hiddenWhileEligible()).isEqualTo(new MetricsReport.HiddenRate(0, 1, 0.0));
        assertThat(report.forecast()).isNull();
    }

    @Test
    void reviewForOlderRevisionIsIgnored() {
        EvalLabel label = new EvalLabel("가톨릭대학교", 1L, "A", "BASIC", null, "x", null, null, null);
        List<MetricsCalculator.Sample> samples = List.of(
                new MetricsCalculator.Sample(label, ExtractionStatus.DONE, card(true), null, false, 3));

        MetricsReport report = MetricsCalculator.compute("model", samples, PERSONAS,
                Map.of(1L, new MetricsCalculator.Review(true, null, 2)), List.of());

        assertThat(report.extraction().target()).isNull();
        assertThat(report.extraction().conditions()).isNull();
        assertThat(report.verdict()).isNull();
    }

    @Test
    void hiddenWhileEligibleCountsServiceHidingEligiblePairs() {
        EvalLabel label = new EvalLabel("가톨릭대학교", 1L, "A", "FULL", null, "x", List.of(), List.of(),
                Map.of("P1", "ELIGIBLE"));
        List<MetricsCalculator.Sample> samples = List.of(new MetricsCalculator.Sample(label, ExtractionStatus.DONE,
                card(true, cond("GRADE", "IN", "1")), null, false, 1));

        MetricsReport report = MetricsCalculator.compute("model", samples, PERSONAS, Map.of(), List.of());

        assertThat(report.hiddenWhileEligible()).isEqualTo(new MetricsReport.HiddenRate(1, 1, 1.0));
        assertThat(report.verdict()).isEqualTo(new MetricsReport.Score(0, 1, 0.0));
    }
}
