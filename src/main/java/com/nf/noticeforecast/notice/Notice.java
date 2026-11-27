package com.nf.noticeforecast.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.nf.noticeforecast.attachment.AttachmentText;
import com.nf.noticeforecast.extract.card.Card;
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

    /** 마스킹한 첨부 텍스트(CLAUDE.md 8.5). 아직 받지 않았으면 null. 본문 해시나 첨부 목록이 바뀌면 null로 돌려 다시 받는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<AttachmentText> attachmentTexts;

    private Instant deadlineAt;

    /** 마감에 시각이 없으면 true. 이때 deadline_at은 그날 00:00 KST이고 그날이 끝날 때까지 마감 전으로 본다(11/27 결정). */
    @Column(nullable = false)
    private boolean deadlineAllDay;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String extractionStatus;

    @Getter(AccessLevel.NONE)
    @Column(length = 30)
    private String extractionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    private Card card;

    private Instant extractedAt;

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
                   ExtractionReason extractionReason, Instant now) {
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
        this.extractionReason = extractionReason == null ? null : extractionReason.name();
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

    public ExtractionReason getExtractionReason() {
        return extractionReason == null ? null : ExtractionReason.valueOf(extractionReason);
    }

    public boolean hasAttachments() {
        return attachments != null && !attachments.isEmpty();
    }

    public boolean attachmentsCollected() {
        return attachmentTexts != null;
    }

    public void applyAttachmentTexts(List<AttachmentText> texts, Instant now) {
        this.attachmentTexts = List.copyOf(texts);
        this.updatedAt = now;
    }

    /** 변경 감지(notice_change)는 다음 작업에서 붙인다. 지금은 카드가 바뀔 때마다 revision을 올린다. */
    public void applyCard(Card card, Instant deadlineAt, boolean deadlineAllDay, Instant now) {
        this.card = card;
        this.deadlineAt = deadlineAt;
        this.deadlineAllDay = deadlineAt != null && deadlineAllDay;
        this.extractionStatus = ExtractionStatus.DONE.name();
        this.extractionReason = null;
        this.revision++;
        this.extractedAt = now;
        this.updatedAt = now;
    }

    /** 카드는 null로 두고, 제목에서 읽은 마감(deadline_at)은 그대로 둔다(CLAUDE.md 8.1). */
    public void markNeedsOriginal(ExtractionReason reason, Instant now) {
        this.card = null;
        this.extractionStatus = ExtractionStatus.NEEDS_ORIGINAL.name();
        this.extractionReason = reason.name();
        this.extractedAt = now;
        this.updatedAt = now;
    }

    public boolean refreshBody(Notice recrawled, Instant now) {
        if (Objects.equals(bodyHash, recrawled.bodyHash) && Objects.equals(attachments, recrawled.attachments)) {
            return false;
        }
        this.attachmentTexts = null;
        this.title = recrawled.title;
        this.bodyText = recrawled.bodyText;
        this.bodyHash = recrawled.bodyHash;
        this.attachments = recrawled.attachments;
        // 일반 공지는 다시 받은 값에 마감이 없다(AI가 채운다). 재추출 전까지 마감 임박 알림과 마감 전 변경 확인에서
        // 빠지지 않도록, 제목에서 새 마감을 읽은 경우에만 바꾼다.
        if (recrawled.deadlineAt != null) {
            this.deadlineAt = recrawled.deadlineAt;
            this.deadlineAllDay = recrawled.deadlineAllDay;
        }
        this.extractionStatus = recrawled.extractionStatus;
        this.extractionReason = recrawled.extractionReason;
        this.updatedAt = now;
        return true;
    }
}
