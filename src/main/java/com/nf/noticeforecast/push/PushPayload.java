package com.nf.noticeforecast.push;

import java.util.List;

/**
 * 서비스 워커가 받는 페이로드(CLAUDE.md 9.1). 빠른 동작은 PUT /devices/me/statuses/{noticeId}를 부른다.
 *
 * @param url 눌렀을 때 열 화면 경로. 예: /notices/1024
 */
public record PushPayload(PushType type, String title, String body, String url, Long noticeId, List<String> actions) {

    private static final List<String> QUICK_ACTIONS = List.of("PLANNED", "DISMISSED");

    public static PushPayload forNotice(PushType type, String title, String body, Long noticeId) {
        return new PushPayload(type, title, body, "/notices/" + noticeId, noticeId, QUICK_ACTIONS);
    }
}
