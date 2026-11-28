package com.nf.noticeforecast.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "series_follow")
@IdClass(SeriesFollow.Key.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeriesFollow {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String deviceId;

    @Id
    private Long seriesId;

    // enum CHECK 제약을 피하려고 문자열로 저장하고 getter에서 enum으로 바꾼다.
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String notify;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String source;

    @Column(nullable = false)
    private Instant createdAt;

    public static SeriesFollow of(String deviceId, Long seriesId, FollowNotify notify, FollowSource source, Instant now) {
        SeriesFollow follow = new SeriesFollow();
        follow.deviceId = deviceId;
        follow.seriesId = seriesId;
        follow.notify = notify.name();
        follow.source = source.name();
        follow.createdAt = now;
        return follow;
    }

    public void updateNotify(FollowNotify notify) {
        this.notify = notify.name();
    }

    public FollowNotify getNotify() {
        return FollowNotify.valueOf(notify);
    }

    public FollowSource getSource() {
        return FollowSource.valueOf(source);
    }

    public record Key(String deviceId, Long seriesId) implements Serializable {
    }
}
