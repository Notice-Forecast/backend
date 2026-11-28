package com.nf.noticeforecast.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보낸 푸시 기록(CLAUDE.md 6.4, 9.1). unique(device_id, type, notice_id, series_id, revision)로 같은 알림을 두 번 보내지 않는다.
 * MySQL unique는 NULL끼리 같다고 보지 않아서, 해당 없는 notice_id·series_id·revision은 NULL 대신 0으로 저장한다.
 */
@Getter
@Entity
@Table(name = "push_log", uniqueConstraints = @UniqueConstraint(name = "uk_push_log_once",
        columnNames = {"device_id", "type", "notice_id", "series_id", "revision"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushLog {

    static final long NONE = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String deviceId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private long noticeId;

    @Column(nullable = false)
    private long seriesId;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false)
    private Instant sentAt;

    public static PushLog of(String deviceId, PushType type, Long noticeId, Long seriesId, int revision, Instant sentAt) {
        PushLog log = new PushLog();
        log.deviceId = deviceId;
        log.type = type.name();
        log.noticeId = noticeId == null ? NONE : noticeId;
        log.seriesId = seriesId == null ? NONE : seriesId;
        log.revision = revision;
        log.sentAt = sentAt;
        return log;
    }
}
