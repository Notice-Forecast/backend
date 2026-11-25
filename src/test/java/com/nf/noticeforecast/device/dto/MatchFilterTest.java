package com.nf.noticeforecast.device.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.device.EnrollmentStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchFilterTest {

    @Test
    void 추가_전공에서_주전공과_중복을_뺀다() {
        MatchFilter filter = new MatchFilter(" 컴퓨터정보공학부 ",
                List.of("경영학과", " 경영학과", "컴퓨터정보공학부"), 3, EnrollmentStatus.ENROLLED);

        MatchFilter normalized = filter.normalized();

        assertThat(normalized.department()).isEqualTo("컴퓨터정보공학부");
        assertThat(normalized.extraDepartments()).containsExactly("경영학과");
    }
}
