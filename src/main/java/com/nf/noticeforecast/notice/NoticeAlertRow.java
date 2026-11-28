package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.extract.card.Card;
import java.time.Instant;

/** 준비 시작·마감 임박 알림에 쓰는 공지 요약. 본문·첨부 텍스트는 크기가 커서 읽지 않는다. */
public record NoticeAlertRow(Long id, String title, Instant deadlineAt, boolean deadlineAllDay, int revision,
                             Card card) {
}
