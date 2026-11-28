package com.nf.noticeforecast.forecast;

/** 예보 기준점(CLAUDE.md 6.4, 11/28 결정). 회차 게시일을 학기 시작 또는 학기 끝에서 며칠인지로 바꿔 계산한다. */
public enum SeriesAnchor {
    TERM_START,
    TERM_END
}
