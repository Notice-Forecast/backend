package com.nf.noticeforecast.crawl;

public record FetchResult(int status, String body) {

    public boolean ok() {
        return status == 200;
    }
}
