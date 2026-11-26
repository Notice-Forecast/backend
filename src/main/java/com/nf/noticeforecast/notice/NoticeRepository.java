package com.nf.noticeforecast.notice;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    boolean existsBySourceIdAndArticleNo(Long sourceId, Long articleNo);

    Optional<Notice> findBySourceIdAndArticleNo(Long sourceId, Long articleNo);
}
