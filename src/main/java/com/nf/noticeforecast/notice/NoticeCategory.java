package com.nf.noticeforecast.notice;

import java.util.Map;

public enum NoticeCategory {
    ACADEMIC, SCHOLARSHIP, CAREER, EVENT, DORM, GENERAL, DEPARTMENT;

    private static final Map<String, NoticeCategory> BOARD_LABELS = Map.of(
            "학사", ACADEMIC,
            "장학", SCHOLARSHIP,
            "취창업", CAREER,
            "일반", GENERAL
    );

    public static NoticeCategory fromBoardLabel(String label) {
        return label == null ? null : BOARD_LABELS.get(label.strip());
    }
}
