package com.nf.noticeforecast.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String DEVICE_ID = "deviceId";

    @Bean
    public OpenAPI noticeForecastOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notice Forecast API")
                        .description("공지예보(Notice Forecast) 백엔드 REST API")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(DEVICE_ID, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-Device-Id")));
    }
}
