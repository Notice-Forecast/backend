package com.nf.noticeforecast.crawl;

import java.io.IOException;
import java.time.Duration;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class JsoupSchoolSiteClient implements SchoolSiteClient {

    static final String USER_AGENT = "NoticeForecast/1.0 (+https://github.com/Notice-Forecast)";
    private static final int TIMEOUT_MILLIS = 20_000;

    // 대회 규칙: 학교 서버 요청 사이 1초 이상. 모든 학과 사이트가 같은 학교 서버라 사이트 구분 없이 센다.
    private final RequestThrottle throttle = new RequestThrottle(Duration.ofSeconds(1));

    @Override
    public FetchResult get(String url) throws IOException {
        try {
            throttle.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .execute();
        return new FetchResult(response.statusCode(), response.body());
    }

    @Override
    public DownloadResult download(String url, long maxBytes) throws IOException {
        try {
            throttle.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MILLIS)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .ignoreContentType(true)
                .maxBodySize((int) Math.min(Integer.MAX_VALUE, maxBytes + 1))
                .execute();
        int status = response.statusCode();
        String contentType = response.contentType();
        if (status != 200) {
            return new DownloadResult(status, response.header("Location"), contentType, null, false);
        }
        String length = response.header("Content-Length");
        if (length != null && length.matches("\\d+") && Long.parseLong(length) > maxBytes) {
            return new DownloadResult(status, null, contentType, null, true);
        }
        byte[] bytes = response.bodyAsBytes();
        if (bytes.length > maxBytes) {
            return new DownloadResult(status, null, contentType, null, true);
        }
        return new DownloadResult(status, null, contentType, bytes, false);
    }
}
