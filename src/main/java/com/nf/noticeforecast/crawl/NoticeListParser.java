package com.nf.noticeforecast.crawl;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;

@Slf4j
public class NoticeListParser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public List<ListedNotice> parse(String html, String listUrl) {
        Document document = Jsoup.parse(html, listUrl);
        Map<Long, ListedNotice> byArticle = new LinkedHashMap<>();
        for (Element row : document.select("div.bn-list-common01 table > tbody > tr")) {
            Element link = row.selectFirst("a.b-title[data-article-no]");
            Element date = row.selectFirst(".b-m-con .b-date");
            if (link == null || date == null) {
                continue;
            }
            long articleNo;
            LocalDate postedDate;
            try {
                articleNo = Long.parseLong(link.attr("data-article-no").strip());
                postedDate = LocalDate.parse(date.text().strip(), DATE);
            } catch (NumberFormatException | DateTimeException e) {
                log.warn("skip unreadable notice row on {}: articleNo={}, date={}",
                        listUrl, link.attr("data-article-no"), date.text());
                continue;
            }
            Element writer = row.selectFirst(".b-m-con .b-writer");
            Element category = row.selectFirst(".b-m-con .b-cate");
            String viewUrl = listUrl + "?mode=view&articleNo=" + articleNo;
            byArticle.putIfAbsent(articleNo, new ListedNotice(
                    articleNo,
                    link.text().strip(),
                    category == null ? null : category.text().strip(),
                    postedDate,
                    writer == null ? null : writer.text().strip(),
                    viewUrl,
                    row.hasClass("b-cate-notice")));
        }
        return new ArrayList<>(byArticle.values());
    }
}
