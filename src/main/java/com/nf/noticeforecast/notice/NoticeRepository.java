package com.nf.noticeforecast.notice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
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
     * AI 추출 후보: 아직 추출하지 않은 최근 글(CLAUDE.md 8.1). 본문 해시가 없는 글은 상세를 받지 않은 과거 이력이라 뺀다.
     * 본문도 첨부도 없는 글은 서비스에서 거른다.
     */
    @Query("""
            select n from Notice n
            where n.extractionStatus = 'PENDING'
              and n.bodyHash is not null
              and n.postedAt >= :since
            order by n.postedAt desc
            """)
    List<Notice> findExtractionCandidates(Instant since);
}
