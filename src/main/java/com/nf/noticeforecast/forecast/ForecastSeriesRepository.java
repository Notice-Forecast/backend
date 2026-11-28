package com.nf.noticeforecast.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ForecastSeriesRepository extends JpaRepository<ForecastSeries, Long> {
}
