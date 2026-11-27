package com.nf.noticeforecast.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.attachment.AttachmentText.Status;
import com.nf.noticeforecast.crawl.DownloadResult;
import com.nf.noticeforecast.crawl.FetchResult;
import com.nf.noticeforecast.crawl.SchoolSiteClient;
import com.nf.noticeforecast.notice.NoticeAttachment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttachmentCollectorTest {

    private static final String ROBOTS = "User-agent: *\nDisallow: /_attach/\n";
    private static final String BASE = "https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=download&articleNo=1&attachNo=";

    private static class FakeSite implements SchoolSiteClient {
        final Map<String, DownloadResult> files = new HashMap<>();
        final List<String> downloaded = new ArrayList<>();

        @Override
        public FetchResult get(String url) {
            return url.endsWith("/robots.txt") ? new FetchResult(200, ROBOTS) : new FetchResult(404, "");
        }

        @Override
        public DownloadResult download(String url, long maxBytes) {
            downloaded.add(url);
            return files.getOrDefault(url, new DownloadResult(404, null, null, null, false));
        }
    }

    private static NoticeAttachment attachment(String name, String attachNo) {
        return new NoticeAttachment(name, BASE + attachNo);
    }

    private static DownloadResult file(byte[] bytes) {
        return new DownloadResult(200, null, "application/pdf", bytes, false);
    }

    @Test
    void readsPdfAndMasksContacts() throws Exception {
        FakeSite site = new FakeSite();
        site.files.put(BASE + "1", file(AttachmentTextExtractorTest.pdf("Contact 010-1234-5678 for details")));

        List<AttachmentText> texts = new AttachmentCollector(site).collect("인턴 채용", List.of(attachment("공고.pdf", "1")));

        assertThat(texts).singleElement().satisfies(text -> {
            assertThat(text.status()).isEqualTo(Status.READ);
            assertThat(text.text()).contains("for details").doesNotContain("1234-5678");
        });
    }

    @Test
    void skipsRosterFileNamesWithoutDownloading() {
        FakeSite site = new FakeSite();

        List<AttachmentText> texts = new AttachmentCollector(site)
                .collect("장학생 안내", List.of(attachment("최종 합격자 명단.pdf", "1")));

        assertThat(texts).extracting(AttachmentText::status).containsExactly(Status.SKIPPED_ROSTER);
        assertThat(site.downloaded).isEmpty();
    }

    @Test
    void skipsEveryAttachmentOfRosterNotice() {
        FakeSite site = new FakeSite();

        List<AttachmentText> texts = new AttachmentCollector(site)
                .collect("2026 장학생 선발 결과 안내", List.of(attachment("안내.pdf", "1")));

        assertThat(texts).extracting(AttachmentText::status).containsExactly(Status.SKIPPED_ROSTER);
        assertThat(site.downloaded).isEmpty();
    }

    @Test
    void unsupportedFormatIsNotDownloaded() {
        FakeSite site = new FakeSite();

        List<AttachmentText> texts = new AttachmentCollector(site).collect("기숙사 모집", List.of(attachment("첨부파일.zip", "1")));

        assertThat(texts).extracting(AttachmentText::status).containsExactly(Status.UNSUPPORTED);
        assertThat(site.downloaded).isEmpty();
    }

    @Test
    void tooLargeFileIsSkipped() {
        FakeSite site = new FakeSite();
        site.files.put(BASE + "1", new DownloadResult(200, null, "application/pdf", null, true));

        assertThat(new AttachmentCollector(site).collect("안내", List.of(attachment("요강.pdf", "1"))))
                .extracting(AttachmentText::status).containsExactly(Status.TOO_LARGE);
    }

    @Test
    void followsRedirectInsideSchoolWhenRobotsAllow() throws Exception {
        FakeSite site = new FakeSite();
        site.files.put(BASE + "1", new DownloadResult(302, "/ko/file/2.pdf", null, null, false));
        site.files.put("https://www.catholic.ac.kr/ko/file/2.pdf", file(AttachmentTextExtractorTest.pdf("moved file")));

        assertThat(new AttachmentCollector(site).collect("안내", List.of(attachment("요강.pdf", "1"))))
                .extracting(AttachmentText::status).containsExactly(Status.READ);
    }

    @Test
    void doesNotFollowRedirectIntoRobotsBlockedPath() {
        FakeSite site = new FakeSite();
        site.files.put(BASE + "1", new DownloadResult(302, "/_attach/cuk/file.pdf", null, null, false));

        assertThat(new AttachmentCollector(site).collect("안내", List.of(attachment("요강.pdf", "1"))))
                .extracting(AttachmentText::status).containsExactly(Status.BLOCKED);
        assertThat(site.downloaded).hasSize(1);
    }

    @Test
    void doesNotFollowRedirectOutsideSchool() {
        FakeSite site = new FakeSite();
        site.files.put(BASE + "1", new DownloadResult(302, "https://files.example.com/a.pdf", null, null, false));

        assertThat(new AttachmentCollector(site).collect("안내", List.of(attachment("요강.pdf", "1"))))
                .extracting(AttachmentText::status).containsExactly(Status.BLOCKED);
    }

    @Test
    void failedDownloadIsUnreadable() {
        FakeSite site = new FakeSite();

        assertThat(new AttachmentCollector(site).collect("안내", List.of(attachment("요강.pdf", "9"))))
                .extracting(AttachmentText::status).containsExactly(Status.UNREADABLE);
    }
}
