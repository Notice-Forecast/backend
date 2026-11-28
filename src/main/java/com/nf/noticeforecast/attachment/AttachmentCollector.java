package com.nf.noticeforecast.attachment;

import com.nf.noticeforecast.attachment.AttachmentText.Status;
import com.nf.noticeforecast.crawl.DownloadResult;
import com.nf.noticeforecast.crawl.PersonalInfoMasker;
import com.nf.noticeforecast.crawl.RobotsPolicy;
import com.nf.noticeforecast.crawl.SchoolSiteClient;
import com.nf.noticeforecast.notice.NoticeAttachment;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 공지의 첨부를 내려받아 텍스트를 뽑고 마스킹한다(CLAUDE.md 2장, 8.5). 파일은 저장하지 않는다.
 * 명단성 파일 이름, robots.txt 제한 경로, 10MB 초과, 학교 밖으로의 리다이렉트는 받지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCollector {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final String SCHOOL_DOMAIN = "catholic.ac.kr";
    private static final List<String> ROSTER_FILE_WORDS = List.of("합격자", "수상자", "명단", "선발결과", "선발 결과");

    private final SchoolSiteClient client;
    private final PersonalInfoMasker masker = new PersonalInfoMasker();

    public RobotsPolicy newRobotsPolicy() {
        return new RobotsPolicy(client);
    }

    public List<AttachmentText> collect(String title, List<NoticeAttachment> attachments) {
        return collect(title, attachments, newRobotsPolicy());
    }

    public List<AttachmentText> collect(String title, List<NoticeAttachment> attachments, RobotsPolicy robots) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        boolean rosterNotice = masker.isRosterTitle(title);
        List<AttachmentText> texts = new ArrayList<>();
        for (NoticeAttachment attachment : attachments) {
            String name = attachment.name() == null ? "" : attachment.name();
            if (rosterNotice || ROSTER_FILE_WORDS.stream().anyMatch(name::contains)) {
                texts.add(AttachmentText.of(attachment.name(), Status.SKIPPED_ROSTER));
                continue;
            }
            texts.add(read(attachment, robots));
        }
        return texts;
    }

    private AttachmentText read(NoticeAttachment attachment, RobotsPolicy robots) {
        String name = attachment.name();
        AttachmentTextExtractor.Format format = AttachmentTextExtractor.formatOf(name, null);
        if (format == AttachmentTextExtractor.Format.UNSUPPORTED) {
            return AttachmentText.of(name, Status.UNSUPPORTED);
        }
        String url = attachment.url();
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                if (!schoolUrl(url) || !robots.allows(url)) {
                    return AttachmentText.of(name, Status.BLOCKED);
                }
                DownloadResult result = client.download(url, MAX_BYTES);
                if (result.tooLarge()) {
                    return AttachmentText.of(name, Status.TOO_LARGE);
                }
                if (result.redirect()) {
                    url = URI.create(url).resolve(result.location()).toString();
                    continue;
                }
                if (!result.ok()) {
                    log.warn("attachment {} returned {}", url, result.status());
                    return AttachmentText.of(name, Status.UNREADABLE);
                }
                Optional<String> text = AttachmentTextExtractor.extract(
                        AttachmentTextExtractor.formatOf(name, result.contentType()), result.bytes());
                return text.map(value -> new AttachmentText(name, Status.READ, masker.mask(value)))
                        .orElseGet(() -> AttachmentText.of(name, Status.UNREADABLE));
            }
            return AttachmentText.of(name, Status.UNREADABLE);
        } catch (Exception e) {
            log.warn("attachment {} failed: {}", url, e.toString());
            return AttachmentText.of(name, Status.UNREADABLE);
        }
    }

    static boolean schoolUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && host != null
                    && (host.equals(SCHOOL_DOMAIN) || host.endsWith("." + SCHOOL_DOMAIN));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
