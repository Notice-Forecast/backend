package com.nf.noticeforecast.crawl;

/**
 * @param location  3xx일 때 넘겨 보내는 주소, 아니면 null
 * @param bytes     200이고 크기 제한 안일 때만 채운다
 * @param tooLarge  크기 제한을 넘어 내려받지 않았다
 */
public record DownloadResult(int status, String location, String contentType, byte[] bytes, boolean tooLarge) {

    public boolean ok() {
        return status == 200 && !tooLarge && bytes != null;
    }

    public boolean redirect() {
        return status >= 300 && status < 400 && location != null;
    }
}
