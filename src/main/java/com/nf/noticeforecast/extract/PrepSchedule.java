package com.nf.noticeforecast.extract;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.global.time.KoreanTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * AI가 제안한 준비 시작일(PREP_START)을 서버 규칙으로 확정한다(CLAUDE.md 8.3 추천 일정 규칙, 11/26 결정).
 * <ul>
 *   <li>마감(APPLY_END)이 없으면 PREP_START를 넣지 않는다.</li>
 *   <li>서류가 없으면 마감 N일 전(기본 3일).</li>
 *   <li>서류가 있으면 서류별 "마감 - 기본 소요일" 중 가장 이른 날과 AI 제안 중 더 이른 쪽. OTHER는 AI 제안만 쓴다.</li>
 *   <li>게시일보다 앞이면 게시일로, 마감보다 뒤면 마감 하루 전으로 고친다.</li>
 * </ul>
 */
@Component
public class PrepSchedule {

    static final String PREP_START = "PREP_START";
    static final String RULE = "RULE";
    static final String AI_SUGGESTED = "AI_SUGGESTED";
    private static final String DEFAULT_LABEL = "준비 시작";

    private final PrepScheduleProperties properties;

    public PrepSchedule(PrepScheduleProperties properties) {
        this.properties = properties;
    }

    public Card apply(Card card, LocalDate postedDate) {
        List<ScheduleItem> others = card.schedule().stream().filter(item -> !PREP_START.equals(item.type())).toList();
        Optional<ScheduleItem> suggested = card.schedule().stream().filter(item -> PREP_START.equals(item.type()))
                .findFirst();
        Optional<LocalDate> deadline = deadlineDate(card);
        if (deadline.isEmpty()) {
            return withSchedule(card, others);
        }
        Optional<ScheduleItem> prep = decide(card.documents(), suggested, deadline.get(), postedDate);
        List<ScheduleItem> schedule = new ArrayList<>();
        prep.ifPresent(schedule::add);
        schedule.addAll(others);
        return withSchedule(card, schedule);
    }

    private Optional<ScheduleItem> decide(List<CardDocument> documents, Optional<ScheduleItem> suggested,
                                          LocalDate deadline, LocalDate postedDate) {
        if (postedDate.isAfter(deadline)) {
            return Optional.empty();
        }
        if (documents.isEmpty()) {
            return Optional.of(prepItem(clamp(deadline.minusDays(properties.noDocumentDays()), postedDate, deadline),
                    RULE, null));
        }
        Optional<LocalDate> byRule = documents.stream()
                .map(document -> properties.leadDays().get(document.type()))
                .filter(days -> days != null)
                .map(deadline::minusDays)
                .min(LocalDate::compareTo);
        Optional<LocalDate> byAi = suggested.flatMap(item -> parseDate(item.at()));
        if (byRule.isEmpty() && byAi.isEmpty()) {
            return Optional.empty();
        }
        boolean useRule = byAi.isEmpty() || (byRule.isPresent() && byRule.get().isBefore(byAi.get()));
        LocalDate chosen = useRule ? byRule.get() : byAi.get();
        String label = suggested.map(ScheduleItem::label).orElse(null);
        return Optional.of(prepItem(clamp(chosen, postedDate, deadline), useRule ? RULE : AI_SUGGESTED, label));
    }

    static LocalDate clamp(LocalDate date, LocalDate postedDate, LocalDate deadline) {
        LocalDate result = date;
        if (result.isBefore(postedDate)) {
            result = postedDate;
        }
        if (result.isAfter(deadline)) {
            result = deadline.minusDays(1);
        }
        return result.isBefore(postedDate) ? postedDate : result;
    }

    private static ScheduleItem prepItem(LocalDate date, String source, String label) {
        return new ScheduleItem(PREP_START, label == null || label.isBlank() ? DEFAULT_LABEL : label,
                date.toString(), true, source);
    }

    private static Optional<LocalDate> deadlineDate(Card card) {
        return card.schedule().stream()
                .filter(item -> "APPLY_END".equals(item.type()))
                .findFirst()
                .flatMap(item -> parseDate(item.at()));
    }

    private static Optional<LocalDate> parseDate(String at) {
        if (at == null || at.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(at.length() <= 10 ? LocalDate.parse(at)
                    : OffsetDateTime.parse(at).atZoneSameInstant(KoreanTime.ZONE).toLocalDate());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Card withSchedule(Card card, List<ScheduleItem> schedule) {
        return new Card(card.summary(), card.forStudents(), card.conditions(), schedule, card.documents(),
                card.howToApply());
    }
}
