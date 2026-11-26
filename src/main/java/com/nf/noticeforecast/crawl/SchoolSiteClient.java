package com.nf.noticeforecast.crawl;

import java.io.IOException;

public interface SchoolSiteClient {

    FetchResult get(String url) throws IOException;
}
