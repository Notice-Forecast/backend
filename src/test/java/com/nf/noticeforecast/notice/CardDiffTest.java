package com.nf.noticeforecast.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.notice.CardDiff.CardChange;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CardDiffTest {

    private static final ScheduleItem DEADLINE = end("2026-11-16T18:00:00+09:00");
    private static final CardCondition ENROLLED = condition("ENROLLMENT_STATUS", "IN", List.of("ENROLLED"), "재학생에 한해");
    private static final CardCondition GPA = condition("GPA", "GTE", List.of("3.0"), "평점 3.0 이상");

    private static ScheduleItem end(String at) {
        return new ScheduleItem("APPLY_END", "신청 마감", at, false, "NOTICE");
    }

    private static CardCondition condition(String field, String op, List<String> values, String evidence) {
        return new CardCondition(field, op, values, evidence, "본문");
    }

    private static Card card(List<ScheduleItem> schedule, List<CardCondition> conditions, List<CardDocument> documents) {
        return new Card("요약", true, conditions, schedule, documents, null);
    }

    @Test
    void 마감이_바뀌면_전후_값을_남긴다() {
        List<CardChange> changes = CardDiff.diff(card(List.of(DEADLINE), List.of(), List.of()),
                card(List.of(end("2026-11-18T18:00:00+09:00")), List.of(), List.of()));

        assertThat(changes).containsExactly(
                new CardChange("APPLY_END", "2026-11-16T18:00:00+09:00", "2026-11-18T18:00:00+09:00"));
    }

    @Test
    void 근거_문장_순서_AI_제안_일정만_달라지면_변경이_아니다() {
        Card before = card(List.of(DEADLINE, new ScheduleItem("PREP_START", "준비", "2026-11-10", true, "AI_SUGGESTED")),
                List.of(ENROLLED, GPA), List.of(new CardDocument("성적증명서", "CERTIFICATE", null)));
        Card after = card(List.of(new ScheduleItem("PREP_START", "준비", "2026-11-12", true, "RULE"), DEADLINE),
                List.of(condition("GPA", "GTE", List.of("3.0"), "직전 학기 평점 3.0 이상"), ENROLLED),
                List.of(new CardDocument("성적 증명서 1부", "CERTIFICATE", "발급 필요")));

        assertThat(CardDiff.diff(before, after)).isEmpty();
    }

    @Test
    void 자격_조건_값과_서류_종류가_바뀌면_각각_남긴다() {
        Card before = card(List.of(DEADLINE), List.of(ENROLLED, GPA), List.of(new CardDocument("증명서", "CERTIFICATE", null)));
        Card after = card(List.of(DEADLINE), List.of(ENROLLED, condition("GPA", "GTE", List.of("2.5"), "평점 2.5 이상")),
                List.of(new CardDocument("증명서", "CERTIFICATE", null), new CardDocument("추천서", "RECOMMENDATION", null)));

        assertThat(CardDiff.diff(before, after)).extracting(CardChange::field)
                .containsExactly(CardDiff.CONDITIONS, CardDiff.DOCUMENTS);
    }

    @Test
    void FREE_TEXT는_개수만_보고_학과_이름은_가운뎃점을_무시한다() {
        Card before = card(List.of(), List.of(condition("FREE_TEXT", "MANUAL", List.of(), "지도교수 승인"),
                condition("DEPARTMENT", "IN", List.of("공간디자인ㆍ소비자학과"), "학과")), List.of());
        Card after = card(List.of(), List.of(condition("FREE_TEXT", "MANUAL", List.of(), "교수 승인 필요"),
                condition("DEPARTMENT", "IN", List.of("공간디자인·소비자학과"), "학과")), List.of());

        assertThat(CardDiff.diff(before, after)).isEmpty();
    }

    @Test
    void 같은_마감에_출처만_다르거나_시각을_Z로_적어도_변경이_아니다() {
        Card before = card(List.of(DEADLINE), List.of(), List.of());
        Card sourceOnly = card(List.of(new ScheduleItem("APPLY_END", "마감", "2026-11-16T18:00:00+09:00", false,
                "AI_SUGGESTED")), List.of(), List.of());
        Card utc = card(List.of(end("2026-11-16T09:00:00Z")), List.of(), List.of());

        assertThat(CardDiff.diff(before, sourceOnly)).isEmpty();
        assertThat(CardDiff.diff(before, utc)).isEmpty();
    }

    @Test
    void 이전_카드가_없으면_변경이_아니다() {
        assertThat(CardDiff.diff(null, card(List.of(DEADLINE), List.of(), List.of()))).isEmpty();
    }

    @Test
    void 첫_카드와_바뀐_카드에서만_revision이_오르고_요약이_생긴다() {
        Notice notice = Notice.builder().sourceId(1L).articleNo(1L).title("공지").postedAt(Instant.EPOCH)
                .sourceUrl("https://www.catholic.ac.kr").attachments(List.of())
                .extractionStatus(ExtractionStatus.PENDING).now(Instant.EPOCH).build();
        Card first = card(List.of(DEADLINE), List.of(), List.of());

        notice.applyCard(first, null, false, Instant.EPOCH);
        notice.applyCard(first, null, false, Instant.EPOCH);
        assertThat(notice.getRevision()).isEqualTo(1);
        assertThat(notice.getChangeSummary()).isNull();

        List<CardChange> changes = notice.applyCard(card(List.of(end("2026-11-18T18:00:00+09:00")), List.of(), List.of()),
                null, false, Instant.EPOCH);
        assertThat(changes).hasSize(1);
        assertThat(notice.getRevision()).isEqualTo(2);
        assertThat(notice.getChangeSummary()).isEqualTo("신청 마감이 11/16 18:00에서 11/18 18:00로 바뀌었어요.");
    }
}
