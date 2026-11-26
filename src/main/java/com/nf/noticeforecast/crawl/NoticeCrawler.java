package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ExtractionStatus;
import com.nf.noticeforecast.notice.Notice;
import com.nf.noticeforecast.notice.NoticeCategory;
import com.nf.noticeforecast.notice.NoticeRepository;
import com.nf.noticeforecast.notice.NoticeSource;
import com.nf.noticeforecast.notice.NoticeSourceRepository;
import com.nf.noticeforecast.notice.SourceKind;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeCrawler {

    private static final int PAGE_SIZE = 10;
    private static final int IMAGE_ONLY_TEXT_LENGTH = 50;
    private static final int BACKFILL_MAX_PAGES = 300;
    private static final String BACKFILL_DEPARTMENT_HOST = "csie.";
    private static final LocalDate BACKFILL_SINCE = LocalDate.of(2019, 1, 1);

    private final CrawlProperties properties;
    private final SchoolSiteClient client;
    private final NoticeSourceRepository sourceRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeListParser listParser = new NoticeListParser();
    private final NoticeDetailParser detailParser = new NoticeDetailParser();
    private final PersonalInfoMasker masker = new PersonalInfoMasker();
    private final TitleDeadlineParser titleDeadlineParser = new TitleDeadlineParser();

    /** 모든 사이트의 목록을 보고 새 공지만 상세를 받는다. */
    public synchronized CrawlReport crawlNew() {
        return crawl(source -> true, false, "new");
    }

    /** 해당 종류 사이트의 목록에 보이는 공지를 저장된 것까지 다시 받아 본문 변경을 확인한다. */
    public synchronized CrawlReport recheck(SourceKind kind) {
        return crawl(source -> source.getKind() == kind, true, "recheck " + kind);
    }

    /** 학교 공지 변경 확인과 함께, 목록에서 밀려났어도 마감 전이거나 신청 예정인 공지를 원문 주소로 다시 확인한다. */
    public synchronized CrawlReport recheckUniversityAndActive() {
        RobotsPolicy robots = new RobotsPolicy(client);
        CrawlReport report = new CrawlReport();
        Set<Long> handled = new HashSet<>();
        crawlInto(source -> source.getKind() == SourceKind.UNIVERSITY, true, robots, report, handled);
        recheckStillActive(robots, report, handled);
        log.info("crawl finished (recheck UNIVERSITY + active): {}", report);
        return report;
    }

    private CrawlReport crawl(Predicate<NoticeSource> target, boolean recheckStored, String label) {
        RobotsPolicy robots = new RobotsPolicy(client);
        CrawlReport report = new CrawlReport();
        crawlInto(target, recheckStored, robots, report, new HashSet<>());
        log.info("crawl finished ({}): {}", label, report);
        return report;
    }

    private void crawlInto(Predicate<NoticeSource> target, boolean recheckStored, RobotsPolicy robots,
                           CrawlReport report, Set<Long> handled) {
        for (NoticeSource source : sourceRepository.findAllByEnabledTrueOrderByIdAsc()) {
            if (!included(source) || !target.test(source)) {
                continue;
            }
            try {
                crawlSource(source, robots, report, recheckStored, handled);
            } catch (Exception e) {
                report.failedSources++;
                log.warn("crawl failed for {}: {}", source.getName(), e.toString());
            }
        }
    }

    private boolean included(NoticeSource source) {
        return properties.include().isEmpty() || properties.include().contains(source.host().split("\\.")[0]);
    }

    private void crawlSource(NoticeSource source, RobotsPolicy robots, CrawlReport report, boolean recheckStored,
                             Set<Long> handled) throws IOException {
        if (!robots.allows(source.listUrl())) {
            report.skippedByRobots++;
            return;
        }
        Map<Long, ListedNotice> listed = new LinkedHashMap<>();
        int pages = source.getKind() == SourceKind.UNIVERSITY ? 2 : 1;
        for (int page = 0; page < pages; page++) {
            String url = page == 0 ? source.listUrl()
                    : source.listUrl() + "?mode=list&articleLimit=" + PAGE_SIZE + "&article.offset=" + page * PAGE_SIZE;
            FetchResult result = client.get(url);
            if (!result.ok()) {
                throw new IOException("list " + url + " returned " + result.status());
            }
            List<ListedNotice> rows = listParser.parse(result.body(), source.listUrl());
            if (rows.isEmpty()) {
                log.warn("no notice rows parsed from {}", url);
            }
            rows.forEach(row -> listed.putIfAbsent(row.articleNo(), row));
        }
        report.listed += listed.size();
        for (ListedNotice row : new ArrayList<>(listed.values())) {
            Optional<Notice> existing;
            if (!recheckStored) {
                if (noticeRepository.existsBySourceIdAndArticleNo(source.getId(), row.articleNo())) {
                    continue;
                }
                existing = Optional.empty();
            } else {
                existing = noticeRepository.findBySourceIdAndArticleNo(source.getId(), row.articleNo());
                if (existing.isPresent() && existing.get().getBodyHash() == null) {
                    continue;
                }
            }
            existing.ifPresent(notice -> handled.add(notice.getId()));
            fetchAndStore(source, row, existing, robots, report);
        }
    }

    private void fetchAndStore(NoticeSource source, ListedNotice row, Optional<Notice> existing, RobotsPolicy robots,
                               CrawlReport report) {
        if (!robots.allows(row.viewUrl())) {
            report.skippedByRobots++;
            return;
        }
        try {
            FetchResult detail = client.get(row.viewUrl());
            if (!detail.ok()) {
                report.failedDetails++;
                return;
            }
            Notice crawled = toNotice(source, row, detailParser.parse(detail.body(), row.viewUrl()));
            if (existing.isEmpty()) {
                noticeRepository.save(crawled);
                report.saved++;
            } else {
                report.rechecked++;
                if (existing.get().refreshBody(crawled, Instant.now())) {
                    noticeRepository.save(existing.get());
                    report.changed++;
                }
            }
        } catch (Exception e) {
            report.failedDetails++;
            log.warn("detail failed {}: {}", row.viewUrl(), e.toString());
        }
    }

    private void recheckStillActive(RobotsPolicy robots, CrawlReport report, Set<Long> handled) {
        Map<Long, NoticeSource> sources = new HashMap<>();
        for (Notice notice : noticeRepository.findStillActive(Instant.now())) {
            if (handled.contains(notice.getId())) {
                continue;
            }
            NoticeSource source = sources.computeIfAbsent(notice.getSourceId(),
                    id -> sourceRepository.findById(id).orElse(null));
            if (source == null || !source.isEnabled() || !included(source)) {
                continue;
            }
            ListedNotice row = new ListedNotice(notice.getArticleNo(), notice.getTitle(), null,
                    notice.getPostedAt().atZone(KoreanTime.ZONE).toLocalDate(), notice.getDepartment(),
                    notice.getSourceUrl(), false);
            fetchAndStore(source, row, Optional.of(notice), robots, report);
        }
    }

    /**
     * 예보용 과거 이력을 한 번 모은다(CLAUDE.md 10장). 상세는 받지 않고 목록의 제목·게시일·부서만 저장한다.
     * 정기 수집이 보는 앞 페이지는 건너뛰고, 학교 공지는 끝까지, 컴퓨터정보공학부는 2019년 글까지 본다.
     */
    public synchronized CrawlReport backfill() {
        RobotsPolicy robots = new RobotsPolicy(client);
        CrawlReport report = new CrawlReport();
        for (NoticeSource source : sourceRepository.findAllByEnabledTrueOrderByIdAsc()) {
            boolean university = source.getKind() == SourceKind.UNIVERSITY;
            if (!university && !source.host().startsWith(BACKFILL_DEPARTMENT_HOST)) {
                continue;
            }
            try {
                backfillSource(source, university ? 2 : 1, robots, report);
            } catch (Exception e) {
                report.failedSources++;
                log.warn("backfill failed for {}: {}", source.getName(), e.toString());
            }
        }
        log.info("crawl finished (backfill): {}", report);
        return report;
    }

    private void backfillSource(NoticeSource source, int firstPage, RobotsPolicy robots, CrawlReport report)
            throws IOException {
        if (!robots.allows(source.listUrl())) {
            report.skippedByRobots++;
            return;
        }
        for (int page = firstPage; page < BACKFILL_MAX_PAGES; page++) {
            String url = source.listUrl() + "?mode=list&articleLimit=" + PAGE_SIZE + "&article.offset=" + page * PAGE_SIZE;
            FetchResult result = client.get(url);
            if (!result.ok()) {
                throw new IOException("list " + url + " returned " + result.status());
            }
            List<ListedNotice> rows = listParser.parse(result.body(), source.listUrl()).stream()
                    .filter(row -> !row.pinned())
                    .toList();
            if (rows.isEmpty()) {
                return;
            }
            for (ListedNotice row : rows) {
                if (row.postedDate().isBefore(BACKFILL_SINCE)) {
                    return;
                }
                report.listed++;
                if (noticeRepository.existsBySourceIdAndArticleNo(source.getId(), row.articleNo())) {
                    continue;
                }
                noticeRepository.save(toHistoryNotice(source, row));
                report.saved++;
            }
        }
    }

    Notice toHistoryNotice(NoticeSource source, ListedNotice row) {
        return Notice.builder()
                .sourceId(source.getId())
                .articleNo(row.articleNo())
                .title(row.title())
                .department(row.department())
                .category(source.getKind() == SourceKind.DEPARTMENT
                        ? NoticeCategory.DEPARTMENT : NoticeCategory.fromBoardLabel(row.boardCategory()))
                .postedAt(row.postedDate().atStartOfDay(KoreanTime.ZONE).toInstant())
                .sourceUrl(row.viewUrl())
                .attachments(List.of())
                .extractionStatus(ExtractionStatus.PENDING)
                .now(Instant.now())
                .build();
    }

    Notice toNotice(NoticeSource source, ListedNotice row, NoticeDetail detail) {
        boolean roster = masker.isRosterTitle(row.title());
        boolean imageOnly = detail.bodyText().strip().length() < IMAGE_ONLY_TEXT_LENGTH && detail.imageCount() > 0;
        boolean needsOriginal = roster || (imageOnly && detail.attachments().isEmpty());
        Instant deadline = needsOriginal
                ? titleDeadlineParser.parse(row.title()).orElse(null)
                : null;
        return Notice.builder()
                .sourceId(source.getId())
                .articleNo(row.articleNo())
                .title(row.title())
                .department(row.department())
                .category(source.getKind() == SourceKind.DEPARTMENT
                        ? NoticeCategory.DEPARTMENT : NoticeCategory.fromBoardLabel(row.boardCategory()))
                .postedAt(row.postedDate().atStartOfDay(KoreanTime.ZONE).toInstant())
                .sourceUrl(row.viewUrl())
                .bodyText(roster ? null : masker.mask(detail.bodyText()))
                .bodyHash(roster ? null : BodyHasher.hash(detail.bodyText()))
                .attachments(detail.attachments())
                .deadlineAt(deadline)
                .extractionStatus(needsOriginal ? ExtractionStatus.NEEDS_ORIGINAL : ExtractionStatus.PENDING)
                .now(Instant.now())
                .build();
    }

    @lombok.Getter
    public static class CrawlReport {
        int listed;
        int saved;
        int rechecked;
        int changed;
        int failedDetails;
        int failedSources;
        int skippedByRobots;

        @Override
        public String toString() {
            return "listed=" + listed + ", saved=" + saved + ", rechecked=" + rechecked + ", changed=" + changed
                    + ", failedDetails=" + failedDetails
                    + ", failedSources=" + failedSources + ", skippedByRobots=" + skippedByRobots;
        }
    }
}
