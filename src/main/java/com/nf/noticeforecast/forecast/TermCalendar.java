package com.nf.noticeforecast.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "term_calendar")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermCalendar {

    @Id
    @Column(length = 10)
    private String term;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    public static TermCalendar of(String term, LocalDate startDate, LocalDate endDate) {
        TermCalendar calendar = new TermCalendar();
        calendar.term = term;
        calendar.update(startDate, endDate);
        return calendar;
    }

    public void update(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }
}
