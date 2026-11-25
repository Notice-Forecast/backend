package com.nf.noticeforecast.device;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, String> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into push_subscription (device_id, endpoint, p256dh, auth, updated_at)
            values (:deviceId, :endpoint, :p256dh, :auth, :now)
            on duplicate key update endpoint = :endpoint, p256dh = :p256dh, auth = :auth, updated_at = :now
            """)
    void upsert(String deviceId, String endpoint, String p256dh, String auth, Instant now);

    @Modifying
    @Query("delete from PushSubscription p where p.deviceId = :deviceId")
    void deleteByDeviceId(String deviceId);
}
