package com.nf.noticeforecast.push;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PushEndpointPolicyTest {

    @Test
    void 브라우저_푸시_서버만_받는다() {
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com/fcm/send/abc")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://updates.push.services.mozilla.com/wpush/v2/abc")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://web.push.apple.com/QGxx")).isTrue();
        assertThat(PushEndpointPolicy.allowed("https://wns2-pn1p.notify.windows.com/w/?token=abc")).isTrue();
    }

    @Test
    void 내부망_다른_서버_http_포트_사용자정보는_막는다() {
        assertThat(PushEndpointPolicy.allowed("https://localhost/push")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://169.254.169.254/latest")).isFalse();
        assertThat(PushEndpointPolicy.allowed("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com:8443/fcm/send/abc")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://user@fcm.googleapis.com/fcm/send/abc")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://fcm.googleapis.com.evil.com/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed("https://evilnotify.windows.com/x")).isFalse();
        assertThat(PushEndpointPolicy.allowed(null)).isFalse();
    }
}
