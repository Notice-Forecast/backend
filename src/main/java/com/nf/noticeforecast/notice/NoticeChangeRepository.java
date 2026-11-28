package com.nf.noticeforecast.notice;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeChangeRepository extends JpaRepository<NoticeChange, Long> {

    List<NoticeChange> findAllByNoticeIdOrderByRevisionDescIdAsc(Long noticeId);
}
