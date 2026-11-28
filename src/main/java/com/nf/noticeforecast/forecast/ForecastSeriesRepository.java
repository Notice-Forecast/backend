package com.nf.noticeforecast.forecast;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForecastSeriesRepository extends JpaRepository<ForecastSeries, Long> {

    List<ForecastSeries> findAllBySeedTrueOrderByIdAsc();
}
