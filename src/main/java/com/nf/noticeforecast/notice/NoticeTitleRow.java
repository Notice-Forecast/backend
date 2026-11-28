package com.nf.noticeforecast.notice;

import java.time.Instant;

/** 계열 판별에 쓰는 공지 요약. category는 저장된 문자열 그대로다. */
public record NoticeTitleRow(Long id, Long seriesId, String title, String department, String category,
                             Instant postedAt) {
}
