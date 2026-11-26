package com.nf.noticeforecast.crawl;

import java.time.LocalDate;

public record ListedNotice(long articleNo, String title, String boardCategory, LocalDate postedDate,
                           String department, String viewUrl, boolean pinned) {
}
