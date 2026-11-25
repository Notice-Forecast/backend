package com.nf.noticeforecast.global.ratelimit;

import com.nf.noticeforecast.device.CurrentDeviceArgumentResolver;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RESTORE_PATH = "/api/v1/devices/restore";

    private final FixedWindowRateLimiter deviceLimiter = new FixedWindowRateLimiter(Clock.systemUTC(), 60);
    private final FixedWindowRateLimiter restoreLimiter = new FixedWindowRateLimiter(Clock.systemUTC(), 5);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        if (RESTORE_PATH.equals(request.getServletPath())) {
            check(restoreLimiter, clientIp(request));
        }
        String deviceId = request.getHeader(CurrentDeviceArgumentResolver.HEADER);
        if (deviceId != null && !deviceId.isBlank()) {
            check(deviceLimiter, deviceId);
        }
        return true;
    }

    private void check(FixedWindowRateLimiter limiter, String key) {
        if (!limiter.tryAcquire(key)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    // X-Forwarded-For는 클라이언트가 앞에 값을 끼워 넣을 수 있어 Railway가 채우는 X-Real-IP를 쓴다.
    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        return realIp == null || realIp.isBlank() ? request.getRemoteAddr() : realIp;
    }
}
