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

    /** AI 추출 대상: 아직 추출하지 않았고 본문이 있는 최근 글(CLAUDE.md 8.1). 본문 없이 첨부만 있는 글은 첨부 추출 뒤에 다룬다. */
    @Query("""
            select n from Notice n
            where n.extractionStatus = 'PENDING'
              and n.bodyText is not null and length(trim(n.bodyText)) > 0
              and n.postedAt >= :since
            order by n.postedAt desc
            """)
    List<Notice> findExtractionCandidates(Instant since);
}
