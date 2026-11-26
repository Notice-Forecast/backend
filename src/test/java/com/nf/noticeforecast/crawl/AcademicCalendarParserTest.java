package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcademicCalendarParserTest {

    private static String day(int day, String name, String... campuses) {
        StringBuilder spans = new StringBuilder();
        for (String campus : campuses) {
            spans.append("<span class=\"b-campus01\">").append(campus).append("</span>");
        }
        return "<div><p> " + day + " (월) </p><ul><li> " + name + " <div class=\"b-cal-list-cate\">" + spans
                + "</div></li></ul></div>";
    }

    private static String month(int month, String... days) {
        return "<div><p> " + month + "월 </p><div>" + String.join("", days) + "</div></div>";
    }

    private static String page(String... months) {
        return "<ul><li><a href=\"?mode=list&siteId=ko&selectedYear=2027\">2027</a></li>"
                + "<li><a href=\"?mode=list&siteId=ko&selectedYear=2026\">2026</a></li></ul>"
                + "<div class=\"b-cal-list-box\"><div class=\"b-cal-top-box\"><div><p>월</p></div></div>"
                + String.join("", months) + "</div>";
    }

    private final AcademicCalendarParser parser = new AcademicCalendarParser();

    @Test
    void 개시일과_방학_전날로_학기를_만든다() {
        String html = page(
                month(3, day(3, "1학기 개시일 / 신입생 입학미사", "성심", "성의")),
                month(6, day(23, "여름방학", "성심")),
                month(8, day(31, "2학기 개시일", "성심")),
                month(12, day(21, "겨울방학", "성심", "성신")));

        List<AcademicCalendarParser.Term> terms = parser.parseTerms(html, 2026);

        assertThat(terms).containsExactly(
                new AcademicCalendarParser.Term("2026-1", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 6, 22)),
                new AcademicCalendarParser.Term("2026-2", LocalDate.of(2026, 8, 31), LocalDate.of(2026, 12, 20)));
    }

    @Test
    void 방학_일정이_없으면_끝은_비우고_다른_교정_일정은_무시한다() {
        String html = page(
                month(3, day(4, "1학기 개시일", "성심")),
                month(6, day(20, "여름방학", "성의")));

        assertThat(parser.parseTerms(html, 2024)).containsExactly(
                new AcademicCalendarParser.Term("2024-1", LocalDate.of(2024, 3, 4), null));
    }

    @Test
    void 일월_이월은_다음_해로_본다() {
        String html = page(month(2, day(1, "2학기 개시일", "성심")));

        assertThat(parser.parseTerms(html, 2026).get(0).startDate()).isEqualTo(LocalDate.of(2027, 2, 1));
    }

    @Test
    void 예전_학년도의_학기_개강_표기도_시작으로_본다() {
        String html = page(
                month(3, day(2, "1학기 개강", "성심"), day(11, "개강미사", "성심")),
                month(6, day(21, "여름방학", "성심")),
                month(8, day(30, "2학기 개강", "성심")));

        assertThat(parser.parseTerms(html, 2021)).containsExactly(
                new AcademicCalendarParser.Term("2021-1", LocalDate.of(2021, 3, 2), LocalDate.of(2021, 6, 20)),
                new AcademicCalendarParser.Term("2021-2", LocalDate.of(2021, 8, 30), null));
    }

    @Test
    void 연도_탭을_읽는다() {
        assertThat(parser.availableYears(page())).containsExactly(2027, 2026);
    }

    @Test
    void 방학이_들어간_다른_일정과_없는_날짜는_건너뛴다() {
        String html = page(
                month(3, day(2, "1학기 개시일", "성심")),
                month(5, day(20, "여름방학 기숙사 신청", "성심")),
                month(6, day(22, "여름방학", "성심"), day(31, "잘못된 날짜", "성심")));

        assertThat(parser.parseTerms(html, 2023)).containsExactly(
                new AcademicCalendarParser.Term("2023-1", LocalDate.of(2023, 3, 2), LocalDate.of(2023, 6, 21)));
    }
}
