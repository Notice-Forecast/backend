package com.nf.noticeforecast.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI noticeForecastOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Notice Forecast API")
                        .description("공지예보(Notice Forecast) 백엔드 REST API")
                        .version("v1"));
    }
}
