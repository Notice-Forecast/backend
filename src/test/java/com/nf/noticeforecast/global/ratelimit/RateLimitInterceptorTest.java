package com.nf.noticeforecast.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nf.noticeforecast.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

    private static void call(RateLimitInterceptor interceptor, String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("X-Real-IP", "203.0.113.7");
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void 기기_등록은_IP당_분당_20회까지_받는다() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor();
        for (int i = 0; i < 20; i++) {
            call(interceptor, "POST", "/api/v1/devices");
        }

        assertThatThrownBy(() -> call(interceptor, "POST", "/api/v1/devices"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 복구_제한은_POST에만_건다() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor();
        for (int i = 0; i < 10; i++) {
            call(interceptor, "GET", "/api/v1/devices/restore");
        }
        for (int i = 0; i < 5; i++) {
            call(interceptor, "POST", "/api/v1/devices/restore");
        }

        assertThatThrownBy(() -> call(interceptor, "POST", "/api/v1/devices/restore"))
                .isInstanceOf(BusinessException.class);
        assertThatCode(() -> call(interceptor, "POST", "/api/v1/devices")).doesNotThrowAnyException();
    }
}
