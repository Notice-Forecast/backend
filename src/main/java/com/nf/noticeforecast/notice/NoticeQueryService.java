package com.nf.noticeforecast.notice;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.global.error.BusinessException;
import com.nf.noticeforecast.global.error.ErrorCode;
import com.nf.noticeforecast.global.error.InvalidFieldException;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.dto.ConditionSummary;
import com.nf.noticeforecast.notice.dto.NoticeDetailResponse;
import com.nf.noticeforecast.notice.dto.NoticeListItem;
import com.nf.noticeforecast.notice.dto.NoticeListResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import jakarta.persistence.criteria.Subquery;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 목록·상세(CLAUDE.md 7장, Notion 명세 '공지 API'). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;
    private static final Duration NEW_WINDOW = Duration.ofHours(48);

    private final NoticeRepository noticeRepository;
    private final NoticeSourceRepository noticeSourceRepository;
    private final NoticeStatusRepository noticeStatusRepository;
    private final NoticeChangeRepository noticeChangeRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    public record ListQuery(NoticeCategory category, boolean openOnly, Instant updatedSince, MyStatus myStatus,
                            boolean includeDismissed, String cursor, Integer size) {
    }

    public NoticeListResponse list(Device device, ListQuery query) {
        if (query.myStatus() != null && device == null) {
            throw new BusinessException(ErrorCode.DEVICE_REQUIRED);
        }
        int size = query.size() == null ? DEFAULT_SIZE : query.size();
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidFieldException("size", "1~" + MAX_SIZE + " 사이여야 합니다.");
        }
        NoticeCursor cursor = parseCursor(query.cursor());
        Map<Long, NoticeSource> sources = noticeSourceRepository.findAll().stream()
                .collect(Collectors.toMap(NoticeSource::getId, Function.identity()));
        Set<Long> visibleSources = visibleSourceIds(sources.values(), device);
        Instant now = clock.instant();

        List<NoticeRow> rows = findRows(spec(query, cursor, visibleSources, device, now), size + 1);
        boolean hasNext = rows.size() > size;
        List<NoticeRow> page = hasNext ? rows.subList(0, size) : rows;
        Map<Long, NoticeStatus> statuses = statusesOf(device, page.stream().map(NoticeRow::id).toList());

        List<NoticeListItem> items = page.stream()
                .map(row -> toListItem(row, sources.get(row.sourceId()), statuses.get(row.id()), now))
                .toList();
        String nextCursor = hasNext
                ? new NoticeCursor(page.getLast().postedAt(), page.getLast().id()).encode()
                : null;
        return new NoticeListResponse(items, nextCursor);
    }

    /** 목록에 필요한 컬럼만 읽은 행. 본문·첨부 텍스트는 크기가 커서 목록에서는 읽지 않는다. */
    record NoticeRow(Long id, Long sourceId, String title, String department, String category, Instant postedAt,
                     Instant deadlineAt, boolean deadlineAllDay, int revision, String extractionStatus,
                     String extractionReason, String changeSummary, Card card) {
    }

    /** 커서 방식이라 전체 개수는 필요 없어서 COUNT 없이 limit 개만 받는다. */
    private List<NoticeRow> findRows(Specification<Notice> spec, int limit) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Notice> root = cq.from(Notice.class);
        List<String> columns = List.of("id", "sourceId", "title", "department", "category", "postedAt", "deadlineAt",
                "deadlineAllDay", "revision", "extractionStatus", "extractionReason", "changeSummary", "card");
        cq.multiselect(columns.stream().<Selection<?>>map(name -> root.get(name).alias(name)).toList())
                .where(spec.toPredicate(root, cq, cb))
                .orderBy(cb.desc(root.get("postedAt")), cb.desc(root.get("id")));
        return entityManager.createQuery(cq).setMaxResults(limit).getResultList().stream()
                .map(t -> new NoticeRow(t.get("id", Long.class), t.get("sourceId", Long.class),
                        t.get("title", String.class), t.get("department", String.class),
                        t.get("category", String.class), t.get("postedAt", Instant.class),
                        t.get("deadlineAt", Instant.class), t.get("deadlineAllDay", Boolean.class),
                        t.get("revision", Integer.class), t.get("extractionStatus", String.class),
                        t.get("extractionReason", String.class), t.get("changeSummary", String.class),
                        t.get("card", Card.class)))
                .toList();
    }

    public NoticeDetailResponse detail(Device device, Long id) {
        Notice notice = noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND));
        String sourceName = noticeSourceRepository.findById(notice.getSourceId()).map(NoticeSource::getName).orElse(null);
        MyStatus myStatus = device == null ? null
                : noticeStatusRepository.findById(new NoticeStatus.Key(device.getId(), id))
                        .map(NoticeStatus::getStatus).orElse(null);
        return new NoticeDetailResponse(
                notice.getId(), notice.getArticleNo(), notice.getTitle(), notice.getDepartment(), sourceName,
                notice.getCategory(), KoreanTime.from(notice.getPostedAt()), notice.getSourceUrl(),
                deadlineText(notice), notice.getAttachments() == null ? List.of() : notice.getAttachments(),
                notice.getRevision(), KoreanTime.from(notice.getUpdatedAt()), false,
                new NoticeDetailResponse.Extraction(notice.getExtractionStatus(), notice.getExtractionReason(),
                        KoreanTime.from(notice.getExtractedAt())),
                notice.getCard(), notice.getChangeSummary(), changesOf(id), null, myStatus);
    }

    private List<NoticeDetailResponse.Change> changesOf(Long noticeId) {
        return noticeChangeRepository.findAllByNoticeIdOrderByRevisionDescIdAsc(noticeId).stream()
                .map(change -> new NoticeDetailResponse.Change(change.getRevision(), KoreanTime.from(change.getChangedAt()),
                        change.getField(), change.getBeforeValue(), change.getAfterValue()))
                .toList();
    }

    /** 본교 공지는 모두, 학과 공지는 기기의 주전공·추가 전공과 이름이 같은 사이트만(기기·matchFilter가 없으면 본교만). */
    static Set<Long> visibleSourceIds(Iterable<NoticeSource> sources, Device device) {
        Set<String> departments = new HashSet<>();
        if (device != null && device.getMatchDepartment() != null) {
            departments.add(DepartmentNames.normalize(device.getMatchDepartment()));
            device.getMatchExtraDepartments().forEach(name -> departments.add(DepartmentNames.normalize(name)));
        }
        Set<Long> ids = new HashSet<>();
        for (NoticeSource source : sources) {
            if (source.getKind() == SourceKind.UNIVERSITY
                    || departments.contains(DepartmentNames.normalize(source.getName()))) {
                ids.add(source.getId());
            }
        }
        return ids;
    }

    private Specification<Notice> spec(ListQuery query, NoticeCursor cursor, Set<Long> visibleSources, Device device,
                                       Instant now) {
        Instant todayStart = LocalDate.ofInstant(now, KoreanTime.ZONE).atStartOfDay(KoreanTime.ZONE).toInstant();
        return (root, criteriaQuery, cb) -> {
            List<Predicate> where = new ArrayList<>();
            // 예보용 과거 이력(본문을 받지 않은 글)은 목록에 내지 않는다(11/28 결정).
            where.add(cb.isNotNull(root.get("bodyHash")));
            where.add(visibleSources.isEmpty() ? cb.disjunction() : root.get("sourceId").in(visibleSources));
            if (query.category() != null) {
                where.add(cb.equal(root.get("category"), query.category().name()));
            }
            if (query.openOnly()) {
                where.add(cb.or(
                        cb.isNull(root.get("deadlineAt")),
                        cb.and(cb.isFalse(root.get("deadlineAllDay")), cb.greaterThan(root.get("deadlineAt"), now)),
                        cb.and(cb.isTrue(root.get("deadlineAllDay")),
                                cb.greaterThanOrEqualTo(root.get("deadlineAt"), todayStart))));
            }
            if (query.updatedSince() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), query.updatedSince()));
            }
            if (device != null) {
                if (query.myStatus() != null) {
                    where.add(cb.exists(statusSubquery(root, criteriaQuery, cb, device, query.myStatus())));
                } else if (!query.includeDismissed()) {
                    where.add(cb.not(cb.exists(statusSubquery(root, criteriaQuery, cb, device, MyStatus.DISMISSED))));
                }
            }
            if (cursor != null) {
                where.add(cb.or(
                        cb.lessThan(root.get("postedAt"), cursor.postedAt()),
                        cb.and(cb.equal(root.get("postedAt"), cursor.postedAt()), cb.lessThan(root.get("id"), cursor.id()))));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static Subquery<Long> statusSubquery(Root<Notice> root, CriteriaQuery<?> query, CriteriaBuilder cb,
                                                 Device device, MyStatus status) {
        Subquery<Long> sub = query.subquery(Long.class);
        var s = sub.from(NoticeStatus.class);
        return sub.select(s.get("noticeId")).where(
                cb.equal(s.get("noticeId"), root.get("id")),
                cb.equal(s.get("deviceId"), device.getId()),
                cb.equal(s.get("status"), status.name()));
    }

    private Map<Long, NoticeStatus> statusesOf(Device device, List<Long> noticeIds) {
        if (device == null || noticeIds.isEmpty()) {
            return Map.of();
        }
        List<NoticeStatus.Key> keys = noticeIds.stream().map(id -> new NoticeStatus.Key(device.getId(), id)).toList();
        return noticeStatusRepository.findAllById(keys).stream()
                .collect(Collectors.toMap(NoticeStatus::getNoticeId, Function.identity()));
    }

    private static NoticeListItem toListItem(NoticeRow row, NoticeSource source, NoticeStatus status, Instant now) {
        Card card = row.card();
        List<ConditionSummary> conditions = card == null ? List.of()
                : card.conditions().stream().map(c -> new ConditionSummary(c.field(), c.op(), c.values())).toList();
        return new NoticeListItem(
                row.id(), row.title(), row.department(), source == null ? null : source.getName(),
                row.category() == null ? null : NoticeCategory.valueOf(row.category()),
                KoreanTime.from(row.postedAt()), deadlineText(row.deadlineAt(), row.deadlineAllDay()), row.revision(),
                !row.postedAt().isBefore(now.minus(NEW_WINDOW)), ExtractionStatus.valueOf(row.extractionStatus()),
                row.extractionReason() == null ? null : ExtractionReason.valueOf(row.extractionReason()),
                row.changeSummary(),
                card == null ? null : card.forStudents(), conditions,
                status == null ? null : status.getStatus(),
                status == null ? null : KoreanTime.from(status.getUpdatedAt()));
    }

    static String deadlineText(Notice notice) {
        return deadlineText(notice.getDeadlineAt(), notice.isDeadlineAllDay());
    }

    /** 시각이 있으면 +09:00 시각, 날짜만 있는 마감이면 날짜만 내보낸다(CLAUDE.md 4장, 11/27 결정). */
    static String deadlineText(Instant deadlineAt, boolean allDay) {
        if (deadlineAt == null) {
            return null;
        }
        if (allDay) {
            return LocalDate.ofInstant(deadlineAt, KoreanTime.ZONE).toString();
        }
        // OffsetDateTime.toString()은 초가 0이면 초를 빼서, 다른 시각 필드와 같은 형식(…:00+09:00)이 되도록 포맷터를 쓴다.
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(KoreanTime.from(deadlineAt));
    }

    /**
     * 주소의 쿼리에서 '+'는 공백으로 바뀌므로, "2026-11-28T00:00:00 09:00"처럼 온 값은 '+'로 되돌려 읽는다.
     * Z(UTC) 형식도 받는다.
     */
    public static Instant parseUpdatedSince(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value.strip().replace(' ', '+')).toInstant();
        } catch (DateTimeParseException e) {
            throw new InvalidFieldException("updatedSince", "2026-11-28T00:00:00+09:00 형식이어야 합니다.");
        }
    }

    private static NoticeCursor parseCursor(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NoticeCursor.decode(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidFieldException("cursor", "올바른 cursor가 아닙니다.");
        }
    }
}
