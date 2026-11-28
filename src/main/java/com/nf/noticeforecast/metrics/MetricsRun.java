package com.nf.noticeforecast.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 실측 한 번의 결과. GET /metrics는 가장 최근 것을 준다(CLAUDE.md 15.2). */
@Getter
@Entity
@Table(name = "metrics_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetricsRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant computedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private MetricsReport report;

    public static MetricsRun of(MetricsReport report, Instant computedAt) {
        MetricsRun run = new MetricsRun();
        run.report = report;
        run.computedAt = computedAt;
        return run;
    }
}
