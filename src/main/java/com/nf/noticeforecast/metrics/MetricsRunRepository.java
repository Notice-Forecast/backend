package com.nf.noticeforecast.metrics;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricsRunRepository extends JpaRepository<MetricsRun, Long> {

    Optional<MetricsRun> findFirstByOrderByComputedAtDescIdDesc();
}
