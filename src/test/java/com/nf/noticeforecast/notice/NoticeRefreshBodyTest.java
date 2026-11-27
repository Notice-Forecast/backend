package com.nf.noticeforecast.notice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeRefreshBodyTest {

    private static final Instant NOW = Instant.parse("2026-11-27T00:00:00Z");
    private static final Instant AI_DEADLINE = Instant.parse("2026-11-30T09:00:00Z");

    private static Notice notice(String hash, Instant deadline) {
        return Notice.builder()
                .sourceId(1L).articleNo(100L).title("장학생 선발").department("학생지원팀")
                .postedAt(NOW).sourceUrl("https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=100")
                .bodyText("본문 " + hash).bodyHash(hash).attachments(List.of())
                .deadlineAt(deadline).extractionStatus(ExtractionStatus.PENDING).now(NOW)
                .build();
    }

    @Test
    void 본문이_바뀌어도_다시_받은_값에_마감이_없으면_기존_마감을_지킨다() {
        Notice stored = notice("a", null);
        stored.applyCard(null, AI_DEADLINE, false, NOW);

        assertThat(stored.refreshBody(notice("b", null), NOW)).isTrue();

        assertThat(stored.getDeadlineAt()).isEqualTo(AI_DEADLINE);
    }

    @Test
    void 제목에서_새_마감을_읽었으면_바꾼다() {
        Notice stored = notice("a", AI_DEADLINE);
        Instant titleDeadline = Instant.parse("2026-12-05T09:00:00Z");

        stored.refreshBody(notice("b", titleDeadline), NOW);

        assertThat(stored.getDeadlineAt()).isEqualTo(titleDeadline);
    }
}
