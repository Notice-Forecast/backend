package com.nf.noticeforecast.crawl;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 학교 학사일정 목록 화면(학년도 = 3월~다음 해 2월)에서 학기 시작·끝을 읽는다(CLAUDE.md 10장).
 * 학기 시작은 "N학기 개시일"(2021학년도는 "N학기 개강"), 학기 끝은 방학 시작 전날이다(11/26 결정). 방학 기숙사 신청처럼 방학이 들어간 다른 일정과 섞이지 않게
 * 이름이 정확히 "여름방학"·"겨울방학"인 항목만 본다. 수집 대상 사이트가 모두
 * 성심교정이라 성심교정 일정만 본다.
 */
public class AcademicCalendarParser {

    private static final String CAMPUS = "성심";
    private static final Pattern MONTH = Pattern.compile("(\\d{1,2})\\s*월");
    private static final Pattern DAY = Pattern.compile("^(\\d{1,2})");
    private static final Pattern TERM_START = Pattern.compile("([12])학기\\s*(?:개시일|개강(?!미사))");
    private static final Pattern YEAR_TAB = Pattern.compile("selectedYear=(\\d{4})");

    public record Term(String term, LocalDate startDate, LocalDate endDate) {
    }

    public List<Integer> availableYears(String html) {
        List<Integer> years = new ArrayList<>();
        Matcher matcher = YEAR_TAB.matcher(html);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (!years.contains(year)) {
                years.add(year);
            }
        }
        return years;
    }

    public List<Term> parseTerms(String html, int academicYear) {
        Map<Integer, LocalDate> starts = new LinkedHashMap<>();
        Map<Integer, LocalDate> ends = new LinkedHashMap<>();
        Document document = Jsoup.parse(html);
        Element box = document.selectFirst("div.b-cal-list-box");
        if (box == null) {
            return List.of();
        }
        for (Element monthBlock : box.children()) {
            if (monthBlock.hasClass("b-cal-top-box") || monthBlock.children().size() < 2) {
                continue;
            }
            Matcher month = MONTH.matcher(monthBlock.child(0).text());
            if (!month.find()) {
                continue;
            }
            int monthValue = Integer.parseInt(month.group(1));
            int year = monthValue >= 3 ? academicYear : academicYear + 1;
            for (Element dayBlock : monthBlock.child(1).children()) {
                Element dateText = dayBlock.selectFirst("p");
                Matcher day = dateText == null ? null : DAY.matcher(dateText.text().strip());
                if (day == null || !day.find()) {
                    continue;
                }
                LocalDate date;
                try {
                    date = LocalDate.of(year, monthValue, Integer.parseInt(day.group(1)));
                } catch (DateTimeException e) {
                    continue;
                }
                for (Element item : dayBlock.select("ul > li")) {
                    boolean campus = item.select(".b-cal-list-cate span").stream()
                            .anyMatch(span -> span.text().contains(CAMPUS));
                    if (!campus) {
                        continue;
                    }
                    String name = item.ownText();
                    Matcher termStart = TERM_START.matcher(name);
                    if (termStart.find()) {
                        starts.putIfAbsent(Integer.parseInt(termStart.group(1)), date);
                    } else if (name.strip().equals("여름방학")) {
                        ends.putIfAbsent(1, date.minusDays(1));
                    } else if (name.strip().equals("겨울방학")) {
                        ends.putIfAbsent(2, date.minusDays(1));
                    }
                }
            }
        }
        List<Term> terms = new ArrayList<>();
        starts.forEach((semester, start) ->
                terms.add(new Term(academicYear + "-" + semester, start, ends.get(semester))));
        return terms;
    }
}
