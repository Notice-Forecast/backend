package com.nf.noticeforecast.crawl;

import java.io.IOException;

public interface SchoolSiteClient {

    FetchResult get(String url) throws IOException;

    /** 파일을 바이트로 받는다. 리다이렉트는 따라가지 않고 그대로 돌려준다. */
    default DownloadResult download(String url, long maxBytes) throws IOException {
        throw new UnsupportedOperationException("download is not supported");
    }
}
