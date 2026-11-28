package com.nf.noticeforecast.notice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** 목록 다음 페이지 위치. 게시일 최신순 + id 내림차순이라 (postedAt, id)로 이어 받는다. 프론트는 값을 그대로 돌려주기만 한다. */
public record NoticeCursor(Instant postedAt, long id) {

    public String encode() {
        String raw = postedAt.getEpochSecond() + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException 읽을 수 없는 값 */
    public static NoticeCursor decode(String value) {
        String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        String[] parts = raw.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("cursor");
        }
        return new NoticeCursor(Instant.ofEpochSecond(Long.parseLong(parts[0])), Long.parseLong(parts[1]));
    }
}
