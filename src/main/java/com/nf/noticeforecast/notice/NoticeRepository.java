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
              and (n.deadlineAt > :now
                   or exists (select 1 from NoticeStatus s where s.noticeId = n.id and s.status = 'PLANNED'))
            """)
    List<Notice> findStillActive(Instant now);
}
