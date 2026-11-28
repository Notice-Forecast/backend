package com.nf.noticeforecast.notice;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NoticeStatusRepository extends JpaRepository<NoticeStatus, NoticeStatus.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into notice_status (device_id, notice_id, status, updated_at)
            values (:deviceId, :noticeId, :status, :now)
            on duplicate key update status = :status, updated_at = :now
            """)
    void upsert(String deviceId, Long noticeId, String status, Instant now);

    @Modifying
    @Query("delete from NoticeStatus s where s.deviceId = :deviceId and s.noticeId = :noticeId")
    void deleteOne(String deviceId, Long noticeId);

    @Modifying
    @Query("delete from NoticeStatus s where s.deviceId = :deviceId")
    void deleteAllByDeviceId(String deviceId);

    List<NoticeStatus> findAllByStatus(String status);

    List<NoticeStatus> findAllByNoticeIdAndStatus(Long noticeId, String status);

    List<NoticeStatus> findAllByNoticeId(Long noticeId);
}
