package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.extract.card.Card;
import java.time.Instant;

/** 예보 계산에 쓰는 공지 요약. 본문·첨부 텍스트는 읽지 않는다. */
public record ForecastNoticeRow(Long id, Long seriesId, Long sourceId, String title, Instant postedAt, Card card) {
}
