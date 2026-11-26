package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.notice.NoticeAttachment;
import java.util.List;

public record NoticeDetail(String bodyText, int imageCount, List<NoticeAttachment> attachments) {
}
