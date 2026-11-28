package com.nf.noticeforecast.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PushLogRepository extends JpaRepository<PushLog, Long> {

    boolean existsByDeviceIdAndTypeAndNoticeIdAndSeriesIdAndRevision(String deviceId, String type, long noticeId,
                                                                     long seriesId, int revision);

    @Transactional
    @Modifying
    @Query("delete from PushLog p where p.deviceId = :deviceId")
    void deleteAllByDeviceId(String deviceId);
}
