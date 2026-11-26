package com.nf.noticeforecast.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 9)
    private String restoreCode;

    @Column(nullable = false)
    private boolean alertNewNotice = true;

    @Column(nullable = false)
    private boolean alertPrepStart = true;

    @Column(nullable = false)
    private boolean alertNoticeChanged = true;

    @Column(nullable = false)
    private boolean alertDeadline = true;

    @Column(length = 100)
    private String matchDepartment;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> matchExtraDepartments;

    private Integer matchGrade;

    // enum 그대로 매핑하면 Hibernate가 허용 값 CHECK 제약을 만들고, ddl-auto=update는 값이 바뀌어도
    // 제약을 고치지 않아 새 값 저장이 실패한다. 그래서 문자열로 저장하고 getter에서 enum으로 바꾼다.
    @Getter(AccessLevel.NONE)
    @Column(length = 30)
    private String matchEnrollmentStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    public static Device register(String restoreCode, Instant now) {
        Device device = new Device();
        device.id = UUID.randomUUID().toString();
        device.restoreCode = restoreCode;
        device.createdAt = now;
        device.lastSeenAt = now;
        return device;
    }

    public void updateAlerts(boolean newNotice, boolean prepStart, boolean noticeChanged, boolean deadline) {
        this.alertNewNotice = newNotice;
        this.alertPrepStart = prepStart;
        this.alertNoticeChanged = noticeChanged;
        this.alertDeadline = deadline;
    }

    public void updateMatchFilter(String department, List<String> extraDepartments, int grade,
                                  EnrollmentStatus enrollmentStatus) {
        this.matchDepartment = department;
        this.matchExtraDepartments = extraDepartments;
        this.matchGrade = grade;
        this.matchEnrollmentStatus = enrollmentStatus.name();
    }

    public List<String> getMatchExtraDepartments() {
        return matchExtraDepartments == null ? List.of() : matchExtraDepartments;
    }

    public EnrollmentStatus getMatchEnrollmentStatus() {
        return matchEnrollmentStatus == null ? null : EnrollmentStatus.valueOf(matchEnrollmentStatus);
    }

    public boolean hasMatchFilter() {
        return matchDepartment != null;
    }
}
