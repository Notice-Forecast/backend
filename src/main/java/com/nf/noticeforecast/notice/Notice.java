package com.nf.noticeforecast.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "notice",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_source_article", columnNames = {"source_id", "article_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sourceId;

    @Column(nullable = false)
    private Long articleNo;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 100)
    private String department;

    // enum 그대로 매핑하면 Hibernate가 허용 값 CHECK 제약을 만들고, ddl-auto=update는 값이 바뀌어도
    // 제약을 고치지 않아 새 값 저장이 실패한다. 그래서 문자열로 저장하고 getter에서 enum으로 바꾼다.
    @Getter(AccessLevel.NONE)
    @Column(length = 20)
    private String category;

    @Column(nullable = false)
    private Instant postedAt;

    @Column(nullable = false, length = 500)
    private String sourceUrl;

    @Column(length = 16_777_215)
    private String bodyText;

    @Column(length = 64)
    private String bodyHash;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<NoticeAttachment> attachments;

    private Instant deadlineAt;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String extractionStatus;

    @Column(nullable = false)
    private int revision;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Builder
    private Notice(Long sourceId, Long articleNo, String title, String department, NoticeCategory category,
                   Instant postedAt, String sourceUrl, String bodyText, String bodyHash,
                   List<NoticeAttachment> attachments, Instant deadlineAt, ExtractionStatus extractionStatus,
                   Instant now) {
        this.sourceId = sourceId;
        this.articleNo = articleNo;
        this.title = title;
        this.department = department;
        this.category = category == null ? null : category.name();
        this.postedAt = postedAt;
        this.sourceUrl = sourceUrl;
        this.bodyText = bodyText;
        this.bodyHash = bodyHash;
        this.attachments = attachments;
        this.deadlineAt = deadlineAt;
        this.extractionStatus = extractionStatus.name();
        this.revision = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public NoticeCategory getCategory() {
        return category == null ? null : NoticeCategory.valueOf(category);
    }

    public ExtractionStatus getExtractionStatus() {
        return ExtractionStatus.valueOf(extractionStatus);
    }

    public boolean refreshBody(Notice recrawled, Instant now) {
        if (Objects.equals(bodyHash, recrawled.bodyHash)) {
            return false;
        }
        this.title = recrawled.title;
        this.bodyText = recrawled.bodyText;
        this.bodyHash = recrawled.bodyHash;
        this.attachments = recrawled.attachments;
        this.deadlineAt = recrawled.deadlineAt;
        this.extractionStatus = recrawled.extractionStatus;
        this.updatedAt = now;
        return true;
    }
}
