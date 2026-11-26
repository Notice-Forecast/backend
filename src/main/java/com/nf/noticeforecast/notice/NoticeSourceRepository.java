package com.nf.noticeforecast.notice;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeSourceRepository extends JpaRepository<NoticeSource, Long> {

    Optional<NoticeSource> findByBaseUrlAndListPath(String baseUrl, String listPath);

    List<NoticeSource> findAllByEnabledTrueOrderByIdAsc();
}
