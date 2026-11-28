package com.nf.noticeforecast.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사람이 표시하는 실측 항목(대상 맞음/틀림, 서류 맞힌 개수, CLAUDE.md 15.2). 표시할 때의 카드 revision을 함께 둬서,
 * 카드가 바뀌면 실측에서 빼고 다시 표시하게 한다.
 */
@Getter
@Entity
@Table(name = "metrics_review")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MetricsReview {

    @Id
    private Long articleNo;

    private Boolean targetCorrect;

    private Integer documentsMatched;

    @Column(nullable = false)
    private int noticeRevision;

    @Column(nullable = false)
    private Instant updatedAt;

    public static MetricsReview of(Long articleNo, Boolean targetCorrect, Integer documentsMatched, int noticeRevision,
                                   Instant now) {
        MetricsReview review = new MetricsReview();
        review.articleNo = articleNo;
        review.targetCorrect = targetCorrect;
        review.documentsMatched = documentsMatched;
        review.noticeRevision = noticeRevision;
        review.updatedAt = now;
        return review;
    }
}
