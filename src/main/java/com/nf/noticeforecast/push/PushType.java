package com.nf.noticeforecast.push;

/** 푸시 종류(CLAUDE.md 6.2). 자격 변경은 NOTICE_CHANGED에 합친다(11/25 결정). */
public enum PushType {
    NEW_NOTICE,
    NOTICE_CHANGED,
    PREP_START,
    DEADLINE,
    SERIES_POSTED
}
