package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MyStatusTest {

    @Test
    void 신청_완료는_APPLIED다() {
        assertThat(MyStatus.parse("APPLIED")).isEqualTo(MyStatus.APPLIED);
    }

    @Test
    void 예전_값_DONE은_거부한다() {
        assertThatThrownBy(() -> MyStatus.parse("DONE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATUS));
    }
}
