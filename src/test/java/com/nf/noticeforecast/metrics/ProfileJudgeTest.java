package com.nf.noticeforecast.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfileJudgeTest {

    private static final Persona CS_JUNIOR = new Persona("컴퓨터정보공학부", List.of(), 3, "ENROLLED", 3.8, false, false);
    private static final Persona DESIGN_WITH_CS = new Persona("공간디자인ㆍ소비자학과", List.of("컴퓨터정보공학부"), 2,
            "ENROLLED", 3.2, true, false);
    private static final Persona RETURNING = new Persona("경영학과", List.of(), 4, "RETURNING", 2.8, false, true);

    @Test
    void allSatisfiedIsEligible() {
        Card card = card(true, cond("GRADE", "IN", "3", "4"), cond("GPA", "GTE", "3.0"), cond("INTERNATIONAL", "IN", "FALSE"));

        assertThat(ProfileJudge.judge(card, CS_JUNIOR)).isEqualTo(Verdict.ELIGIBLE);
    }

    @Test
    void manualConditionMakesConditionalButSelfChoiceIsIgnored() {
        assertThat(ProfileJudge.judge(card(true, cond("FREE_TEXT", "MANUAL")), CS_JUNIOR)).isEqualTo(Verdict.CONDITIONAL);
        assertThat(ProfileJudge.judge(card(true, cond("SELF_CHOICE", "MANUAL")), CS_JUNIOR)).isEqualTo(Verdict.ELIGIBLE);
    }

    @Test
    void anyFailureHides() {
        Card card = card(true, cond("FREE_TEXT", "MANUAL"), cond("GPA", "GTE", "4.0"));

        assertThat(ProfileJudge.judge(card, CS_JUNIOR)).isEqualTo(Verdict.HIDDEN);
    }

    @Test
    void notForStudentsHides() {
        assertThat(ProfileJudge.judge(card(false), CS_JUNIOR)).isEqualTo(Verdict.HIDDEN);
    }

    @Test
    void extraDepartmentOnlyIsConditionalAndNamesIgnoreMiddleDots() {
        Card cs = card(true, cond("DEPARTMENT", "IN", "컴퓨터정보공학부"));
        Card design = card(true, cond("DEPARTMENT", "IN", "공간디자인·소비자학과"));

        assertThat(ProfileJudge.judge(cs, DESIGN_WITH_CS)).isEqualTo(Verdict.CONDITIONAL);
        assertThat(ProfileJudge.judge(design, DESIGN_WITH_CS)).isEqualTo(Verdict.ELIGIBLE);
        assertThat(ProfileJudge.judge(design, CS_JUNIOR)).isEqualTo(Verdict.HIDDEN);
    }

    @Test
    void notInFailsOnMainDepartmentAndIsConditionalOnExtraOnly() {
        Card excludesCs = card(true, cond("DEPARTMENT", "NOT_IN", "컴퓨터정보공학부"));

        assertThat(ProfileJudge.judge(excludesCs, CS_JUNIOR)).isEqualTo(Verdict.HIDDEN);
        assertThat(ProfileJudge.judge(excludesCs, DESIGN_WITH_CS)).isEqualTo(Verdict.CONDITIONAL);
        assertThat(ProfileJudge.judge(card(true, cond("DEPARTMENT", "NOT_IN", "경영학과")), CS_JUNIOR))
                .isEqualTo(Verdict.ELIGIBLE);
    }

    @Test
    void returningCountsAsLeave() {
        assertThat(ProfileJudge.judge(card(true, cond("ENROLLMENT_STATUS", "IN", "LEAVE")), RETURNING))
                .isEqualTo(Verdict.ELIGIBLE);
        assertThat(ProfileJudge.judge(card(true, cond("ENROLLMENT_STATUS", "IN", "ENROLLED")), RETURNING))
                .isEqualTo(Verdict.HIDDEN);
    }

    @Test
    void flagsAndMissingProfileValues() {
        assertThat(ProfileJudge.judge(card(true, cond("DORM_RESIDENT", "IN", "TRUE")), DESIGN_WITH_CS))
                .isEqualTo(Verdict.ELIGIBLE);
        assertThat(ProfileJudge.judge(card(true, cond("DORM_RESIDENT", "IN", "TRUE")), CS_JUNIOR))
                .isEqualTo(Verdict.HIDDEN);
        assertThat(ProfileJudge.judge(card(true, cond("SEMESTER_COUNT", "GTE", "4")), CS_JUNIOR))
                .isEqualTo(Verdict.CONDITIONAL);
    }

    static Card card(boolean forStudents, CardCondition... conditions) {
        return new Card("요약", forStudents, List.of(conditions), List.of(), List.of(), null);
    }

    static CardCondition cond(String field, String op, String... values) {
        return new CardCondition(field, op, List.of(values), "근거", "본문");
    }
}
