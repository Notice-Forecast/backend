package com.nf.noticeforecast.notice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.EnrollmentStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoticeQueryServiceTest {

    private static NoticeSource source(long id, String name, SourceKind kind) {
        NoticeSource source = NoticeSource.of(name, "https://" + id + ".catholic.ac.kr", "/notice.do", kind);
        ReflectionTestUtils.setField(source, "id", id);
        return source;
    }

    private static final List<NoticeSource> SOURCES = List.of(
            source(1, "가톨릭대학교", SourceKind.UNIVERSITY),
            source(2, "컴퓨터정보공학부", SourceKind.DEPARTMENT),
            source(3, "공간디자인ㆍ소비자학과", SourceKind.DEPARTMENT),
            source(4, "경영학과", SourceKind.DEPARTMENT));

    private static Device device(String department, List<String> extra) {
        Device device = Device.register("ABCD-EFGH", Instant.parse("2026-11-28T00:00:00Z"));
        if (department != null) {
            device.updateMatchFilter(department, extra, 2, EnrollmentStatus.ENROLLED);
        }
        return device;
    }

    private static Notice notice(Instant deadline, boolean allDay) {
        Notice notice = Notice.builder()
                .sourceId(1L).articleNo(1L).title("공지").postedAt(Instant.parse("2026-11-20T15:00:00Z"))
                .sourceUrl("https://www.catholic.ac.kr").attachments(List.of())
                .deadlineAt(deadline).extractionStatus(ExtractionStatus.PENDING).now(Instant.now())
                .build();
        ReflectionTestUtils.setField(notice, "deadlineAllDay", allDay);
        return notice;
    }

    @Test
    void 기기가_없거나_matchFilter가_없으면_본교만_보인다() {
        assertThat(NoticeQueryService.visibleSourceIds(SOURCES, null)).containsExactly(1L);
        assertThat(NoticeQueryService.visibleSourceIds(SOURCES, device(null, List.of()))).containsExactly(1L);
    }

    @Test
    void 주전공과_추가_전공_학과_사이트가_보이고_가운뎃점_표기는_무시한다() {
        Device device = device("컴퓨터정보공학부", List.of("공간디자인·소비자학과"));

        assertThat(NoticeQueryService.visibleSourceIds(SOURCES, device)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void 시각이_있는_마감은_초까지_날짜만_있는_마감은_날짜만_내보낸다() {
        assertThat(NoticeQueryService.deadlineText(notice(Instant.parse("2026-11-21T08:00:00Z"), false)))
                .isEqualTo("2026-11-21T17:00:00+09:00");
        assertThat(NoticeQueryService.deadlineText(notice(Instant.parse("2026-11-20T15:00:00Z"), true)))
                .isEqualTo("2026-11-21");
        assertThat(NoticeQueryService.deadlineText(notice(null, false))).isNull();
    }

    @Test
    void cursor는_게시일과_id를_되돌린다() {
        NoticeCursor cursor = new NoticeCursor(Instant.parse("2026-11-20T15:00:00Z"), 1024L);

        assertThat(NoticeCursor.decode(cursor.encode())).isEqualTo(cursor);
        assertThatThrownBy(() -> NoticeCursor.decode("!!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatedSince는_공백으로_바뀐_플러스와_Z_형식도_읽는다() {
        Instant expected = Instant.parse("2026-11-27T15:00:00Z");

        assertThat(NoticeQueryService.parseUpdatedSince("2026-11-28T00:00:00+09:00")).isEqualTo(expected);
        assertThat(NoticeQueryService.parseUpdatedSince("2026-11-28T00:00:00 09:00")).isEqualTo(expected);
        assertThat(NoticeQueryService.parseUpdatedSince("2026-11-27T15:00:00Z")).isEqualTo(expected);
        assertThat(NoticeQueryService.parseUpdatedSince(null)).isNull();
        assertThatThrownBy(() -> NoticeQueryService.parseUpdatedSince("어제"))
                .isInstanceOf(com.nf.noticeforecast.global.error.InvalidFieldException.class);
    }
}
