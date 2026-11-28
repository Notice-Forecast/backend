package com.nf.noticeforecast.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.notice.CardDiff.CardChange;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeSummariesTest {

    @Test
    void 항목별_문구를_이어_붙인다() {
        String summary = ChangeSummaries.of(List.of(
                new CardChange("APPLY_END", "2026-11-16", "2026-11-18"),
                new CardChange(CardDiff.CONDITIONS, "GPA GTE 3.0", "GPA GTE 2.5")));

        assertThat(summary).isEqualTo("신청 마감이 11/16에서 11/18로 바뀌었어요. 자격 조건이 바뀌었어요.");
    }

    @Test
    void 추가와_삭제와_서류를_구분한다() {
        assertThat(ChangeSummaries.sentence(new CardChange("EVENT", null, "2026-12-01T14:00:00+09:00")))
                .isEqualTo("행사 일정이 12/1 14:00로 추가됐어요.");
        assertThat(ChangeSummaries.sentence(new CardChange("APPLY_START", "2026-11-10", null)))
                .isEqualTo("신청 시작이 빠졌어요.");
        assertThat(ChangeSummaries.sentence(new CardChange(CardDiff.DOCUMENTS, "CERTIFICATE", "CERTIFICATE, OTHER")))
                .isEqualTo("제출 서류가 바뀌었어요.");
    }

    @Test
    void 변경이_없으면_null() {
        assertThat(ChangeSummaries.of(List.of())).isNull();
    }
}
