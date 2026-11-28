package com.nf.noticeforecast.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계열 판별 기록(CLAUDE.md 6.4, 11/28 결정: 별도 테이블). 새 공지와 후보 공지 한 쌍마다 LLM이 같은 공지의 다른 회차인지와
 * 그 이유를 남긴다. 발표에서 "왜 같은 계열로 묶었나"를 보여 줄 때 쓴다.
 */
@Getter
@Entity
@Table(name = "series_judgment", indexes = @Index(name = "idx_series_judgment_notice", columnList = "notice_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeriesJudgment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long noticeId;

    @Column(nullable = false)
    private Long candidateNoticeId;

    /** 후보 공지가 속한 계열 */
    @Column(nullable = false)
    private Long candidateSeriesId;

    @Column(nullable = false)
    private double similarity;

    @Column(nullable = false)
    private boolean same;

    @Column(length = 500)
    private String reason;

    @Column(length = 60)
    private String model;

    @Column(nullable = false)
    private Instant judgedAt;

    public static SeriesJudgment of(Long noticeId, Long candidateNoticeId, Long candidateSeriesId, double similarity,
                                    boolean same, String reason, String model, Instant judgedAt) {
        SeriesJudgment judgment = new SeriesJudgment();
        judgment.noticeId = noticeId;
        judgment.candidateNoticeId = candidateNoticeId;
        judgment.candidateSeriesId = candidateSeriesId;
        judgment.similarity = similarity;
        judgment.same = same;
        judgment.reason = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 500);
        judgment.model = model;
        judgment.judgedAt = judgedAt;
        return judgment;
    }
}
