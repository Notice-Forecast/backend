package com.nf.noticeforecast.notice;

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

    // enum 그대로 매핑하면 Hibernate가 허용 값 CHECK 제약을 만들고, ddl-auto=update는 값이 바뀌어도
    // 제약을 고치지 않아 새 값 저장이 실패한다. 그래서 문자열로 저장하고 getter에서 enum으로 바꾼다.
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant updatedAt;

    public MyStatus getStatus() {
        return MyStatus.valueOf(status);
    }

    public record Key(String deviceId, Long noticeId) implements Serializable {
    }
}
