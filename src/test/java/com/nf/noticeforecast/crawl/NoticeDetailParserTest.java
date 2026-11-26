package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.notice.NoticeAttachment;
import org.junit.jupiter.api.Test;

class NoticeDetailParserTest {

    private static final String VIEW_URL = "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=1";

    @Test
    void 본문_줄을_살리고_이미지와_첨부를_센다() {
        String html = """
                <div class="b-con-box"><div class="fr-view"><p>1. 지원자격</p><p>- 평점&nbsp; 4.0 이상<br>- 재학생</p>
                <img src="/_attach/cuk/editor-image/a.png"></div></div>
                <div class="b-file-box"><ul><li><a class="file-down-btn pdf" href="?mode=download&amp;articleNo=1&amp;attachNo=2">
                 계획.pdf </a></li></ul></div>
                """;

        NoticeDetail detail = new NoticeDetailParser().parse(html, VIEW_URL);

        assertThat(detail.bodyText()).isEqualTo("1. 지원자격\n- 평점 4.0 이상\n- 재학생");
        assertThat(detail.imageCount()).isEqualTo(1);
        assertThat(detail.attachments()).containsExactly(new NoticeAttachment("계획.pdf",
                "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=download&articleNo=1&attachNo=2"));
    }

    @Test
    void 본문_영역이_없으면_빈_본문이다() {
        NoticeDetail detail = new NoticeDetailParser().parse("<div>다른 화면</div>", VIEW_URL);

        assertThat(detail.bodyText()).isEmpty();
        assertThat(detail.attachments()).isEmpty();
    }
}
