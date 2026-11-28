package com.nf.noticeforecast.notice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    boolean existsBySourceIdAndArticleNo(Long sourceId, Long articleNo);

    Optional<Notice> findBySourceIdAndArticleNo(Long sourceId, Long articleNo);

    @Query("""
            select n from Notice n
            where n.bodyHash is not null
              and ((n.deadlineAllDay = false and n.deadlineAt > :now)
                   or (n.deadlineAllDay = true and n.deadlineAt >= :todayStart)
                   or exists (select 1 from NoticeStatus s where s.noticeId = n.id and s.status = 'PLANNED'))
            """)
    /** @param todayStart 오늘 00:00 KST. 날짜만 있는 마감은 그날이 끝날 때까지 마감 전이다. */
    List<Notice> findStillActive(Instant now, Instant todayStart);

    /**
     * AI 추출 후보 ID: 아직 추출하지 않은 최근 글(CLAUDE.md 8.1). 본문 해시가 없는 글은 상세를 받지 않은 과거 이력이라 뺀다.
     * 본문 전체를 한 번에 메모리에 올리지 않고 ID만 먼저 읽는다.
     */
    @Query("""
            select n.id from Notice n
            where n.extractionStatus = 'PENDING'
              and n.bodyHash is not null
              and n.postedAt >= :since
            order by n.postedAt desc
            """)
    List<Long> findExtractionCandidateIds(Instant since);

    /** 계열 판별용. 본문 없이 제목·부서·게시일만 읽는다. 과거 이력도 포함한다. */
    @Query("""
            select new com.nf.noticeforecast.notice.NoticeTitleRow(n.id, n.seriesId, n.title, n.department, n.category,
                n.postedAt)
            from Notice n
            order by n.postedAt asc, n.id asc
            """)
    List<NoticeTitleRow> findAllTitles();

    @Transactional
    @Modifying
    @Query("update Notice n set n.seriesId = :seriesId where n.id = :noticeId")
    void assignSeries(Long noticeId, Long seriesId);

    /** 계열별 회차 수와 첫·마지막 게시일. [seriesId, count, minPostedAt, maxPostedAt] */
    @Query("""
            select n.seriesId, count(n), min(n.postedAt), max(n.postedAt)
            from Notice n
            where n.seriesId is not null
            group by n.seriesId
            having count(n) >= :minRounds
            """)
    List<Object[]> countRoundsBySeries(long minRounds);

    record SeriesStats(long rounds, Instant firstPostedAt, Instant lastPostedAt) {
    }

    @Query("""
            select new com.nf.noticeforecast.notice.NoticeRepository$SeriesStats(
                count(n), min(n.postedAt), max(n.postedAt))
            from Notice n
            where n.seriesId = :seriesId
            """)
    SeriesStats summarizeSeries(Long seriesId);

    @Query("""
            select new com.nf.noticeforecast.notice.NoticeTitleRow(n.id, n.seriesId, n.title, n.department, n.category,
                n.postedAt)
            from Notice n
            where n.seriesId = :seriesId
            order by n.postedAt asc, n.id asc
            """)
    List<NoticeTitleRow> findTitlesBySeries(Long seriesId);

    @Transactional
    @Modifying
    @Query("update Notice n set n.seriesId = :to where n.seriesId = :from")
    int moveSeries(Long from, Long to);

    @Query("""
            select new com.nf.noticeforecast.notice.NoticeAlertRow(n.id, n.title, n.deadlineAt, n.deadlineAllDay,
                n.revision, n.card)
            from Notice n
            where n.id in :ids
            """)
    List<NoticeAlertRow> findAlertRows(java.util.Collection<Long> ids);
}
