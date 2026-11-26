package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeListParserTest {

    private static final String LIST_URL = "https://www.catholic.ac.kr/ko/campuslife/notice.do";

    private static String row(String trClass, long articleNo, String title, String category, String date, String writer) {
        String cate = category == null ? "" : "<li><span class=\"b-con b-cate\">" + category + "</span></li>";
        return """
                <tr class="%s"><td>번호</td><td class="b-td-title"><div class="b-title-box">
                <a class="b-title" data-article-no="%d" href="?mode=view&amp;articleNo=%d">
                %s
                </a><div class="b-m-con"><ul>%s<li><span class="b-con b-date">%s</span></li>
                <li><span class="b-con b-writer">%s</span></li></ul></div></div></td></tr>
                """.formatted(trClass, articleNo, articleNo, title, cate, date, writer);
    }

    private static String page(String rows) {
        return "<div class=\"bn-list-common01\"><table><tbody>" + rows + "</tbody></table></div>";
    }

    @Test
    void 고정_공지와_일반_목록에_겹친_글은_한_번만_읽는다() {
        String html = page(
                row(" b-cate-notice", 278177, "[기숙사운영센터] 모집공고", "일반", "2026.11.25", "기숙사운영센터")
                        + row("", 278177, "[기숙사운영센터] 모집공고", "일반", "2026.11.25", "기숙사운영센터")
                        + row("", 278111, "[학사행정팀] 복수전공 변경 안내", "학사", "2026.11.24", "학사행정팀"));

        List<ListedNotice> notices = new NoticeListParser().parse(html, LIST_URL);

        assertThat(notices).hasSize(2);
        ListedNotice first = notices.get(0);
        assertThat(first.articleNo()).isEqualTo(278177);
        assertThat(first.pinned()).isTrue();
        assertThat(first.title()).isEqualTo("[기숙사운영센터] 모집공고");
        assertThat(first.boardCategory()).isEqualTo("일반");
        assertThat(first.postedDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(first.department()).isEqualTo("기숙사운영센터");
        assertThat(first.viewUrl()).isEqualTo(LIST_URL + "?mode=view&articleNo=278177");
        assertThat(notices.get(1).boardCategory()).isEqualTo("학사");
    }

    @Test
    void 분류_열이_없는_학과_목록도_읽는다() {
        String listUrl = "https://csie.catholic.ac.kr/csie/community/notice.do";
        String html = page(row("", 278138, "전문가 특강 안내", null, "2026.11.24", "컴퓨터정보공학부"));

        ListedNotice notice = new NoticeListParser().parse(html, listUrl).get(0);

        assertThat(notice.boardCategory()).isNull();
        assertThat(notice.department()).isEqualTo("컴퓨터정보공학부");
        assertThat(notice.viewUrl()).isEqualTo(listUrl + "?mode=view&articleNo=278138");
    }
}
