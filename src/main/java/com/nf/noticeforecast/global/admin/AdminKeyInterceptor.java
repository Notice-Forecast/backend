package com.nf.noticeforecast.global.admin;

import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Admin-Key";

    private final byte[] adminKey;

    public AdminKeyInterceptor(@Value("${app.admin.key:}") String adminKey) {
        this.adminKey = adminKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String given = request.getHeader(HEADER);
        // 키가 설정되지 않은 환경에서는 내부 API를 모두 막는다.
        if (adminKey.length == 0 || given == null
                || !MessageDigest.isEqual(adminKey, given.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.ADMIN_REQUIRED);
        }
        return true;
    }
}
