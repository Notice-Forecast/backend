package com.nf.noticeforecast.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notice_source",
        uniqueConstraints = @UniqueConstraint(name = "uk_notice_source_url", columnNames = {"base_url", "list_path"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String baseUrl;

    @Column(nullable = false, length = 200)
    private String listPath;

    // enum 그대로 매핑하면 Hibernate가 허용 값 CHECK 제약을 만들고, ddl-auto=update는 값이 바뀌어도
    // 제약을 고치지 않아 새 값 저장이 실패한다. 그래서 문자열로 저장하고 getter에서 enum으로 바꾼다.
    @Getter(AccessLevel.NONE)
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false)
    private boolean enabled;

    public static NoticeSource of(String name, String baseUrl, String listPath, SourceKind kind) {
        NoticeSource source = new NoticeSource();
        source.baseUrl = baseUrl;
        source.listPath = listPath;
        source.update(name, kind, true);
        return source;
    }

    public void update(String name, SourceKind kind, boolean enabled) {
        this.name = name;
        this.kind = kind.name();
        this.enabled = enabled;
    }

    public SourceKind getKind() {
        return SourceKind.valueOf(kind);
    }

    public String listUrl() {
        return baseUrl + listPath;
    }

    public String host() {
        return java.net.URI.create(baseUrl).getHost();
    }
}
