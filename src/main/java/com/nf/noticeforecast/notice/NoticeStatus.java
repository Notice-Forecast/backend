package com.nf.noticeforecast.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "notice_status")
@IdClass(NoticeStatus.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeStatus {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String deviceId;

    @Id
    private Long noticeId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MyStatus status;

    @Column(nullable = false)
    private Instant updatedAt;

    public record Key(String deviceId, Long noticeId) implements Serializable {
    }
}
