package com.nf.noticeforecast.config;

import com.nf.noticeforecast.device.CurrentDeviceArgumentResolver;
import com.nf.noticeforecast.global.admin.AdminKeyInterceptor;
import com.nf.noticeforecast.global.ratelimit.RateLimitInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentDeviceArgumentResolver currentDeviceArgumentResolver;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final AdminKeyInterceptor adminKeyInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentDeviceArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(adminKeyInterceptor).addPathPatterns("/api/v1/internal/**");
    }
}
