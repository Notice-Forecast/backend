package com.nf.noticeforecast.push;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 푸시 구독 주소는 브라우저 회사의 푸시 서버만 받는다. 서버가 이 주소로 직접 요청을 보내기 때문에, 아무 주소나 받으면
 * 내부망이나 다른 서버를 대신 찌르는 데 쓰일 수 있다(SSRF). Chrome·Edge(FCM), Firefox, Safari, Windows(WNS)만 허용한다.
 */
public final class PushEndpointPolicy {

    private static final Set<String> HOSTS = Set.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "web.push.apple.com");
    private static final List<String> HOST_SUFFIXES = List.of(".notify.windows.com", ".push.apple.com");

    private PushEndpointPolicy() {
    }

    public static boolean allowed(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                return false;
            }
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return HOSTS.contains(host) || HOST_SUFFIXES.stream().anyMatch(host::endsWith);
        } catch (Exception e) {
            return false;
        }
    }
}
