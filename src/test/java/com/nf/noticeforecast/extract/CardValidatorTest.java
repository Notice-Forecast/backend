package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.extract.card.CardValidator;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CardValidatorTest {

    private static final String BODY = """
            2. 지원자격
            5학기 이상 재학한 3·4학년 재학생 (2026년 2학기 기준)
            총 평균평점 3.0 이상
            - 신청기간: 2026. 11. 25.(수) ~ 11. 26.(목)까지
            """;

    private static CardCondition condition(String field, String op, List<String> values, String evidence) {
        return new CardCondition(field, op, values, evidence, "본문");
    }

    private static Card card(List<CardCondition> conditions, List<ScheduleItem> schedule) {
        return new Card("CHUBB 장학생은 11/26까지 신청합니다.", true, conditions, schedule,
                List.of(new CardDocument("장학금 신청서", "APPLICATION_FORM", null)), null);
    }

    private static final ScheduleItem APPLY_END = new ScheduleItem("APPLY_END", "신청 마감", "2026-11-26", true, "NOTICE");

    @Test
    void acceptsValidCard() {
        Card card = card(List.of(
                condition("GRADE", "IN", List.of("3", "4"), "3·4학년 재학생"),
                condition("GPA", "GTE", List.of("3.0"), "총 평균평점 3.0 이상"),
                condition("FREE_TEXT", "MANUAL", List.of(), "5학기 이상 재학한")), List.of(APPLY_END));

        assertThat(CardValidator.validate(card, BODY, Map.of())).isEmpty();
    }

    @Test
    void evidenceMatchesIgnoringWhitespaceDifferences() {
        Card card = card(List.of(condition("GPA", "GTE", List.of("3.0"), "총  평균평점\n3.0 이상")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).isEmpty();
    }

    @Test
    void rejectsEvidenceNotInBody() {
        Card card = card(List.of(condition("GPA", "GTE", List.of("3.5"), "평점 3.5 이상")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of()))
                .singleElement().asString().contains("그대로 있지 않습니다");
    }

    @Test
    void rejectsUnknownAttachmentSource() {
        CardCondition fromAttachment = new CardCondition("GPA", "GTE", List.of("3.0"), "총 평균평점 3.0 이상", "첨부: 공고.pdf");

        assertThat(CardValidator.validate(card(List.of(fromAttachment), List.of()), BODY, Map.of()))
                .singleElement().asString().contains("첨부 이름");
    }

    @Test
    void acceptsEvidenceFromAttachment() {
        CardCondition fromAttachment = new CardCondition("FREE_TEXT", "MANUAL", List.of(), "만 34세 이하", "첨부: 공고.pdf");

        assertThat(CardValidator.validate(card(List.of(fromAttachment), List.of()), BODY,
                Map.of("공고.pdf", "지원자격: 만 34세 이하 청년"))).isEmpty();
    }

    @Test
    void freeTextMustBeManualWithoutValues() {
        Card card = card(List.of(condition("FREE_TEXT", "IN", List.of("7구간"), "3·4학년 재학생")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("MANUAL");
    }

    @Test
    void manualIsOnlyForFreeTextAndSelfChoice() {
        Card card = card(List.of(condition("GRADE", "MANUAL", List.of(), "3·4학년 재학생")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("FREE_TEXT");
    }

    @Test
    void gpaNeedsSingleNumber() {
        Card card = card(List.of(condition("GPA", "GTE", List.of("B0"), "총 평균평점 3.0 이상")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("숫자");
    }

    @Test
    void gpaIsOnFourPointFiveScale() {
        Card card = card(List.of(condition("GPA", "GTE", List.of("70"), "총 평균평점 3.0 이상")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("4.5 만점");
    }

    @Test
    void booleanFieldsUseTrueOrFalse() {
        Card card = card(List.of(condition("INTERNATIONAL", "IN", List.of("false"), "3·4학년 재학생")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("TRUE");
    }

    @Test
    void enrollmentValuesMustBeKnown() {
        Card card = card(List.of(condition("ENROLLMENT_STATUS", "IN", List.of("STUDENT"), "3·4학년 재학생")), List.of());

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("ENROLLMENT_STATUS");
    }

    @Test
    void rejectsWrongDateFormat() {
        ScheduleItem timed = new ScheduleItem("APPLY_END", "신청 마감", "2026-11-26 17:00", false, "NOTICE");

        assertThat(CardValidator.validate(card(List.of(), List.of(timed)), BODY, Map.of()))
                .singleElement().asString().contains("형식");
    }

    @Test
    void ruleSourceIsReservedForServer() {
        ScheduleItem rule = new ScheduleItem("PREP_START", "준비 시작", "2026-11-24", true, "RULE");

        assertThat(CardValidator.validate(card(List.of(), List.of(rule)), BODY, Map.of()))
                .singleElement().asString().contains("source");
    }

    @Test
    void onlyOneApplyEnd() {
        ScheduleItem second = new ScheduleItem("APPLY_END", "서류 마감", "2026-11-27T11:00:00+09:00", false, "NOTICE");

        assertThat(CardValidator.validate(card(List.of(), List.of(APPLY_END, second)), BODY, Map.of()))
                .singleElement().asString().contains("하나만");
    }

    @Test
    void requiresForStudents() {
        Card card = new Card("요약", null, List.of(), List.of(), List.of(), null);

        assertThat(CardValidator.validate(card, BODY, Map.of())).singleElement().asString().contains("forStudents");
    }

    @Test
    void rejectsAiSuggestedOnNonPrepSchedule() {
        ScheduleItem guessedDeadline = new ScheduleItem("APPLY_END", "신청 마감", "2026-11-26", true, "AI_SUGGESTED");

        assertThat(CardValidator.validate(card(List.of(), List.of(guessedDeadline)), BODY, Map.of()))
                .singleElement().asString().contains("NOTICE");
    }

    @Test
    void rejectsNaNInfinityAndNegativeNumbers() {
        for (String bad : List.of("NaN", "Infinity", "-Infinity", "-1.0")) {
            Card c = card(List.of(condition("GPA", "GTE", List.of(bad), "총 평균평점 3.0 이상")), List.of());
            assertThat(CardValidator.validate(c, BODY, Map.of())).singleElement().asString().contains("숫자");
        }
    }
}
