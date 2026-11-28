package com.nf.noticeforecast.notice;

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

/** 카드가 바뀐 기록(CLAUDE.md 6.4, 8.4). 새 카드를 저장할 때 이전 카드와 비교해 남긴다. */
@Getter
@Entity
@Table(name = "notice_change", indexes = @Index(name = "idx_notice_change_notice", columnList = "notice_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long noticeId;

    @Column(nullable = false)
    private int revision;

    /** schedule.type 값, CONDITIONS, DOCUMENTS */
    @Column(nullable = false, length = 30)
    private String field;

    @Column(length = 2000)
    private String beforeValue;

    @Column(length = 2000)
    private String afterValue;

    @Column(nullable = false)
    private Instant changedAt;

    public static NoticeChange of(Long noticeId, int revision, CardDiff.CardChange change, Instant changedAt) {
        NoticeChange row = new NoticeChange();
        row.noticeId = noticeId;
        row.revision = revision;
        row.field = change.field();
        row.beforeValue = truncate(change.before());
        row.afterValue = truncate(change.after());
        row.changedAt = changedAt;
        return row;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
