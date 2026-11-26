package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeAttachment;
import com.nf.noticeforecast.notice.NoticeCategory;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.SourceKind;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NoticeCrawlerTest {

    private static final String BASE = "https://www.catholic.ac.kr";
    private static final String LIST = BASE + "/ko/campuslife/notice.do";

    private final Map<String, FetchResult> pages = new HashMap<>();
    private final List<String> requested = new ArrayList<>();
    private final NoticeSourceRepository sourceRepository = mock(NoticeSourceRepository.class);
    private final NoticeRepository noticeRepository = mock(NoticeRepository.class);
    private final NoticeSource source = NoticeSource.of("가톨릭대학교", BASE, "/ko/campuslife/notice.do", SourceKind.UNIVERSITY);

    private NoticeCrawler crawler() {
        ReflectionTestUtils.setField(source, "id", 1L);
        when(sourceRepository.findAllByEnabledTrueOrderByIdAsc()).thenReturn(List.of(source));
        SchoolSiteClient client = url -> {
            requested.add(url);
            return pages.getOrDefault(url, new FetchResult(404, ""));
        };
        return new NoticeCrawler(new CrawlProperties(true, List.of(), List.of()), client, sourceRepository, noticeRepository);
    }

    private static String list(long articleNo, String title) {
        return """
                <div class="bn-list-common01"><table><tbody><tr><td><a class="b-title" data-article-no="%d">%s</a>
                <div class="b-m-con"><span class="b-con b-cate">장학</span><span class="b-con b-date">2026.11.18</span>
                <span class="b-con b-writer">학생지원팀</span></div></td></tr></tbody></table></div>
                """.formatted(articleNo, title);
    }

    @Test
    void 새_공지_확인은_새_글만_상세를_받아_마스킹해_저장한다() {
        pages.put(BASE + "/robots.txt", new FetchResult(200, "User-agent: *\nDisallow: /_attach/\n"));
        pages.put(LIST, new FetchResult(200, list(10, "장학생 선발 안내")));
        pages.put(LIST + "?mode=list&articleLimit=10&article.offset=10", new FetchResult(200, list(11, "이미 저장된 공지")));
        pages.put(LIST + "?mode=view&articleNo=10", new FetchResult(200,
                "<div class=\"b-con-box\"><p>지원자격은 평점 4.0 이상인 재학생이며 신청서를 학생지원팀에 제출합니다.</p><p>문의 a@b.com</p></div>"));
        pages.put(LIST + "?mode=view&articleNo=11", new FetchResult(200,
                "<div class=\"b-con-box\"><p>이미 저장된 공지의 본문이고 바뀐 내용이 없습니다. 신청은 학생지원팀으로 하면 됩니다.</p></div>"));
        Notice stored = crawler().toNotice(source, new ListedNotice(11, "이미 저장된 공지", "장학",
                        LocalDate.of(2026, 11, 18), "학생지원팀", LIST + "?mode=view&articleNo=11", false),
                new NoticeDetail("이미 저장된 공지의 본문이고 바뀐 내용이 없습니다. 신청은 학생지원팀으로 하면 됩니다.", 0, List.of()));
        when(noticeRepository.findBySourceIdAndArticleNo(1L, 10L)).thenReturn(java.util.Optional.empty());
        when(noticeRepository.findBySourceIdAndArticleNo(1L, 11L)).thenReturn(java.util.Optional.of(stored));

        crawler().crawlNew();

        assertThat(requested).doesNotContain(LIST + "?mode=view&articleNo=11");
        var captor = org.mockito.ArgumentCaptor.forClass(Notice.class);
        verify(noticeRepository).save(captor.capture());
        Notice saved = captor.getValue();
        assertThat(saved.getArticleNo()).isEqualTo(10L);
        assertThat(saved.getCategory()).isEqualTo(NoticeCategory.SCHOLARSHIP);
        assertThat(saved.getBodyText()).endsWith("문의 [가림]");
        assertThat(saved.getBodyHash()).hasSize(64);
        assertThat(saved.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(saved.getPostedAt()).isEqualTo(Instant.parse("2026-11-17T15:00:00Z"));
    }

    @Test
    void 변경_확인은_저장된_공지도_다시_받는다() {
        pages.put(BASE + "/robots.txt", new FetchResult(200, "User-agent: *\nDisallow: /_attach/\n"));
        pages.put(LIST, new FetchResult(200, list(11, "이미 저장된 공지")));
        pages.put(LIST + "?mode=list&articleLimit=10&article.offset=10", new FetchResult(200, list(11, "이미 저장된 공지")));
        String body = "모집 기간이 12월 3일까지로 연장되었습니다. 신청서는 학생지원팀에 제출하면 됩니다.";
        pages.put(LIST + "?mode=view&articleNo=11", new FetchResult(200, "<div class=\"b-con-box\"><p>" + body + "</p></div>"));
        NoticeCrawler crawler = crawler();
        Notice stored = crawler.toNotice(source, new ListedNotice(11, "이미 저장된 공지", "장학",
                        LocalDate.of(2026, 11, 18), "학생지원팀", LIST + "?mode=view&articleNo=11", false),
                new NoticeDetail("모집 기간은 11월 30일까지입니다. 신청서는 학생지원팀에 제출하면 됩니다.", 0, List.of()));
        when(noticeRepository.findBySourceIdAndArticleNo(1L, 11L)).thenReturn(java.util.Optional.of(stored));

        crawler.recheck(SourceKind.UNIVERSITY);

        assertThat(requested).contains(LIST + "?mode=view&articleNo=11");
        assertThat(stored.getBodyText()).contains("12월 3일");
        verify(noticeRepository).save(stored);
    }

    @Test
    void 변경_확인은_다른_종류의_사이트를_건드리지_않는다() {
        crawler().recheck(SourceKind.DEPARTMENT);

        assertThat(requested).isEmpty();
    }

    @Test
    void 저장된_공지의_본문이_바뀌면_갱신하고_다시_추출_대기로_둔다() {
        ListedNotice row = new ListedNotice(5, "근로장학생 모집", "장학",
                LocalDate.of(2026, 11, 18), "학생지원팀", LIST + "?mode=view&articleNo=5", false);
        NoticeCrawler crawler = crawler();
        Notice stored = crawler.toNotice(source, row, new NoticeDetail("모집 기간은 11월 30일까지이며 신청서는 학생지원팀에 제출합니다. 문의는 내선으로.", 0, List.of()));
        Notice recrawled = crawler.toNotice(source, row, new NoticeDetail("모집 기간이 12월 3일까지로 연장되었습니다. 신청서는 학생지원팀에 제출합니다.", 0, List.of()));

        assertThat(stored.refreshBody(recrawled, Instant.now())).isTrue();
        assertThat(stored.getBodyText()).contains("12월 3일");
        assertThat(stored.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(stored.refreshBody(recrawled, Instant.now())).isFalse();
    }

    @Test
    void 학과_공지는_학과_공지_분류로_넣는다() {
        NoticeSource department = NoticeSource.of("컴퓨터정보공학부", "https://csie.catholic.ac.kr",
                "/csie/community/notice.do", SourceKind.DEPARTMENT);
        ListedNotice row = new ListedNotice(7, "전문가 특강 안내", null,
                LocalDate.of(2026, 11, 24), "컴퓨터정보공학부", "https://csie.catholic.ac.kr/csie/community/notice.do?mode=view&articleNo=7", false);

        Notice notice = crawler().toNotice(department, row, new NoticeDetail("특강은 11월 26일 목요일 4시에 다솔관에서 열립니다. 학부생 누구나 참석할 수 있습니다.", 0, List.of()));

        assertThat(notice.getCategory()).isEqualTo(NoticeCategory.DEPARTMENT);
    }

    @Test
    void robots가_막으면_목록도_받지_않는다() {
        pages.put(BASE + "/robots.txt", new FetchResult(200, "User-agent: *\nDisallow: /\n"));

        crawler().crawlNew();

        assertThat(requested).containsExactly(BASE + "/robots.txt");
        verify(noticeRepository, never()).save(any());
    }

    @Test
    void 이미지뿐이고_첨부가_없으면_원문_확인_필요와_제목_마감일() {
        NoticeCrawler crawler = crawler();
        ListedNotice row = new ListedNotice(1, "국가장학금 신청 안내(2026. 11. 24.(화) 9시 ~ 2026. 11. 30.(월) 18시)", "장학",
                LocalDate.of(2026, 11, 18), "학생지원팀", LIST + "?mode=view&articleNo=1", false);

        Notice notice = crawler.toNotice(source, row, new NoticeDetail("", 3, List.of()));

        assertThat(notice.getExtractionStatus()).isEqualTo(ExtractionStatus.NEEDS_ORIGINAL);
        assertThat(notice.getDeadlineAt()).isEqualTo(Instant.parse("2026-11-30T09:00:00Z"));
    }

    @Test
    void 이미지뿐이어도_첨부가_있으면_다음_단계로_넘긴다() {
        NoticeCrawler crawler = crawler();
        ListedNotice row = new ListedNotice(1, "기숙사 모집공고", "일반",
                LocalDate.of(2026, 11, 25), "기숙사운영센터", LIST + "?mode=view&articleNo=1", false);

        Notice notice = crawler.toNotice(source, row,
                new NoticeDetail("", 20, List.of(new NoticeAttachment("공고.pdf", LIST + "?mode=download"))));

        assertThat(notice.getExtractionStatus()).isEqualTo(ExtractionStatus.PENDING);
        assertThat(notice.getDeadlineAt()).isNull();
    }

    @Test
    void 명단성_공지는_본문을_저장하지_않는다() {
        NoticeCrawler crawler = crawler();
        ListedNotice row = new ListedNotice(1, "동계방학 기숙사 합격자 발표", "일반",
                LocalDate.of(2026, 11, 24), "기숙사운영센터", LIST + "?mode=view&articleNo=1", false);

        Notice notice = crawler.toNotice(source, row, new NoticeDetail("합격자는 첨부를 확인하세요. 입사 일정은 추후 안내합니다. 문의는 기숙사운영센터로.", 0, List.of()));

        assertThat(notice.getBodyText()).isNull();
        assertThat(notice.getBodyHash()).isNull();
        assertThat(notice.getExtractionStatus()).isEqualTo(ExtractionStatus.NEEDS_ORIGINAL);
    }
}
