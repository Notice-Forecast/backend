package com.nf.noticeforecast.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** `npx web-push generate-vapid-keys`로 만든 키(CLAUDE.md 9.1). subject는 연락처(https://github.com/Notice-Forecast, 11/28 결정). */
@ConfigurationProperties(prefix = "app.vapid")
public record VapidProperties(String publicKey, String privateKey, String subject) {

    public boolean configured() {
        return notBlank(publicKey) && notBlank(privateKey) && notBlank(subject);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
