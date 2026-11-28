package com.nf.noticeforecast.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 매 학기 반복되는 공지 계열(CLAUDE.md 6.4). 새 공지가 어느 계열에도 붙지 않으면 그 공지를 첫 회차로 새로 만든다(11/28 결정).
 * 시드(seed)는 자동으로 묶인 계열 중 사람이 골라 이름과 기준점을 붙인 것이다(11/28 결정).
 */
@Getter
@Entity
@Table(name = "forecast_series")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForecastSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String department;

    @Column(length = 20)
    private String category;

    /** 첫 회차의 정규화 제목을 띄어쓰기로 나눈 단어 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> titleKeywords;

    /** 예보 기준점(학기 시작/끝 등). 시드로 고를 때 정한다. 정하기 전에는 null */
    @Column(length = 30)
    private String anchor;

    @Column(nullable = false)
    private boolean seed;

    @Column(nullable = false)
    private Instant createdAt;

    public static ForecastSeries startFrom(String normalizedTitle, String fallbackTitle, String department,
                                           String category, Instant now) {
        ForecastSeries series = new ForecastSeries();
        String name = normalizedTitle.isBlank() ? fallbackTitle : normalizedTitle;
        series.name = name.length() > 200 ? name.substring(0, 200) : name;
        series.department = department;
        series.category = category;
        series.titleKeywords = normalizedTitle.isBlank() ? List.of() : List.of(normalizedTitle.split(" "));
        series.createdAt = now;
        return series;
    }
}
