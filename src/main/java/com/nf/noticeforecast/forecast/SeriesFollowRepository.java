package com.nf.noticeforecast.forecast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SeriesFollowRepository extends JpaRepository<SeriesFollow, SeriesFollow.Key> {

    List<SeriesFollow> findAllByDeviceId(String deviceId);

    Optional<SeriesFollow> findByDeviceIdAndSeriesId(String deviceId, Long seriesId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into series_follow (device_id, series_id, notify, source, created_at)
            values (:deviceId, :seriesId, :notify, :source, :now)
            on duplicate key update notify = :notify
            """)
    void upsert(String deviceId, Long seriesId, String notify, String source, Instant now);

    @Modifying
    @Query("delete from SeriesFollow f where f.deviceId = :deviceId and f.seriesId = :seriesId")
    void deleteOne(String deviceId, Long seriesId);

    @Modifying
    @Query("delete from SeriesFollow f where f.deviceId = :deviceId")
    void deleteAllByDeviceId(String deviceId);
}
