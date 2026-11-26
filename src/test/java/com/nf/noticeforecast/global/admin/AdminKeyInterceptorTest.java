package com.nf.noticeforecast.global.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminKeyInterceptorTest {

    private static boolean pass(AdminKeyInterceptor interceptor, String header) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/internal/crawl/run");
        if (header != null) {
            request.addHeader(AdminKeyInterceptor.HEADER, header);
        }
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    void 키가_맞으면_통과한다() {
        assertThat(pass(new AdminKeyInterceptor("secret"), "secret")).isTrue();
    }

    @Test
    void 키가_없거나_다르면_ADMIN_REQUIRED() {
        AdminKeyInterceptor interceptor = new AdminKeyInterceptor("secret");
        for (String header : new String[]{null, "", "wrong"}) {
            assertThatThrownBy(() -> pass(interceptor, header))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ADMIN_REQUIRED));
        }
    }

    @Test
    void 서버에_키가_설정되지_않으면_모두_막는다() {
        assertThatThrownBy(() -> pass(new AdminKeyInterceptor(""), ""))
                .isInstanceOf(BusinessException.class);
    }
}
