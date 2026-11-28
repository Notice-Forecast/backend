package com.nf.noticeforecast.notice;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReportRepository extends JpaRepository<Report, Long> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into report (notice_id, device_id, reason, comment, created_at)
            values (:noticeId, :deviceId, :reason, :comment, :now)
            on duplicate key update reason = :reason, comment = :comment, created_at = :now
            """)
    void upsert(Long noticeId, String deviceId, String reason, String comment, Instant now);

    @Modifying
    @Query("update Report r set r.deviceId = null where r.deviceId = :deviceId")
    void detachDevice(String deviceId);
}
