package com.nf.noticeforecast.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final String DEFAULT_LOCAL_ORIGIN = "http://localhost:3000";

    private final String allowedOriginsRaw;

    public CorsConfig(@Value("${app.cors.allowed-origins:}") String allowedOriginsRaw) {
        this.allowedOriginsRaw = allowedOriginsRaw;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Set<String> origins = new LinkedHashSet<>();
        origins.add(DEFAULT_LOCAL_ORIGIN);

        if (allowedOriginsRaw != null && !allowedOriginsRaw.isBlank()) {
            Arrays.stream(allowedOriginsRaw.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }

        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "Accept", "X-Device-Id", "X-Admin-Key")
                .exposedHeaders("X-Device-Id")
                .maxAge(3600);
    }
}
