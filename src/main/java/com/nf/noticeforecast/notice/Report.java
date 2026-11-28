package com.nf.noticeforecast.notice;

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
 * 판정·추출 오류 신고(CLAUDE.md 6.4). 같은 기기·공지는 덮어쓴다. 기기를 지우면 device_id만 null로 두고 내용은 정확도 점검에 남긴다.
 */
@Getter
@Entity
@Table(name = "report",
        uniqueConstraints = @UniqueConstraint(name = "uk_report_notice_device", columnNames = {"notice_id", "device_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long noticeId;

    @Column(length = 36)
    private String deviceId;

    // enum CHECK 제약을 피하려고 문자열로 저장한다(Notice.category와 같은 이유).
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 30)
    private String reason;

    @Column(length = 200)
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    public ReportReason getReason() {
        return ReportReason.valueOf(reason);
    }
}
