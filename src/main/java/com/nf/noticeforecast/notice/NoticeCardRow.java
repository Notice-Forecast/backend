package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.extract.card.Card;

/** 알림량 실측에 쓰는 공지 요약. 본문·첨부 텍스트는 읽지 않는다. */
public record NoticeCardRow(Long id, Long sourceId, Card card) {
}
