package com.nf.noticeforecast.push;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.EnrollmentStatus;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ServerEligibilityTest {

    private static Device device(String department, List<String> extra, int grade, EnrollmentStatus status) {
        Device device = Device.register("ABCD-EFGH", Instant.now());
        device.updateMatchFilter(department, extra, grade, status);
        return device;
    }

    private static Card card(Boolean forStudents, CardCondition... conditions) {
        return new Card("요약", forStudents, List.of(conditions), List.of(), List.of(), null);
    }

    private static CardCondition condition(String field, String op, String... values) {
        return new CardCondition(field, op, List.of(values), "근거", "본문");
    }

    private final Device csie3 = device("컴퓨터정보공학부", List.of("공간디자인ㆍ소비자학과"), 3, EnrollmentStatus.ENROLLED);

    @Test
    void 확인할_수_있는_조건이_모두_맞거나_확인_불가면_보낸다() {
        assertThat(ServerEligibility.notExcluded(card(true,
                condition("DEPARTMENT", "IN", "컴퓨터정보공학부"),
                condition("GRADE", "IN", "3", "4"),
                condition("GPA", "GTE", "3.5"),
                condition("FREE_TEXT", "MANUAL")), csie3)).isTrue();
    }

    @Test
    void 추가_전공만_맞아도_조건부라_보내고_학과가_다르면_안_보낸다() {
        assertThat(ServerEligibility.notExcluded(card(true, condition("DEPARTMENT", "IN", "공간디자인·소비자학과")), csie3))
                .isTrue();
        assertThat(ServerEligibility.notExcluded(card(true, condition("DEPARTMENT", "IN", "경영학과")), csie3)).isFalse();
    }

    @Test
    void 학년_조건과_학부생_대상이_아닌_공지는_거른다() {
        assertThat(ServerEligibility.notExcluded(card(true, condition("GRADE", "GTE", "4")), csie3)).isFalse();
        assertThat(ServerEligibility.notExcluded(card(true, condition("GRADE", "NOT_IN", "3")), csie3)).isFalse();
        assertThat(ServerEligibility.notExcluded(card(false), csie3)).isFalse();
        assertThat(ServerEligibility.notExcluded(null, csie3)).isFalse();
    }

    @Test
    void 복학_예정은_휴학생으로도_본다() {
        Device returning = device("경영학과", List.of(), 2, EnrollmentStatus.RETURNING);

        assertThat(ServerEligibility.notExcluded(card(true, condition("ENROLLMENT_STATUS", "IN", "LEAVE")), returning))
                .isTrue();
        assertThat(ServerEligibility.notExcluded(card(true, condition("ENROLLMENT_STATUS", "IN", "ENROLLED")), returning))
                .isFalse();
    }
}
