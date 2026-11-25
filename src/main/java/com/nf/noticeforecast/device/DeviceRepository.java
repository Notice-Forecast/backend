package com.nf.noticeforecast.device;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceRepository extends JpaRepository<Device, String> {

    Optional<Device> findByRestoreCode(String restoreCode);

    boolean existsByRestoreCode(String restoreCode);

    @Transactional
    @Modifying
    @Query("update Device d set d.lastSeenAt = :now where d.id = :id")
    int touch(String id, Instant now);
}
