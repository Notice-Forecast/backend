package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardDocument;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrepScheduleTest {

    private static final LocalDate POSTED = LocalDate.of(2026, 11, 10);
    private static final ScheduleItem DEADLINE = new ScheduleItem("APPLY_END", "신청 마감",
            "2026-11-21T17:00:00+09:00", false, "NOTICE");

    private final PrepSchedule prepSchedule = new PrepSchedule(PrepScheduleProperties.defaults());

    private static Card card(List<ScheduleItem> schedule, List<CardDocument> documents) {
        return new Card("요약", true, List.of(), schedule, documents, null);
    }

    private static CardDocument document(String type) {
        return new CardDocument("서류", type, null);
    }

    private static ScheduleItem aiPrep(String at) {
        return new ScheduleItem("PREP_START", "추천서 요청", at, true, "AI_SUGGESTED");
    }

    private static ScheduleItem prepOf(Card card) {
        return card.schedule().stream().filter(item -> "PREP_START".equals(item.type())).findFirst().orElse(null);
    }

    @Test
    void 기본_소요일이_AI_제안보다_이르면_규칙을_쓴다() {
        Card result = prepSchedule.apply(card(List.of(aiPrep("2026-11-18"), DEADLINE),
                List.of(document("RECOMMENDATION"))), POSTED);

        assertThat(prepOf(result)).isEqualTo(new ScheduleItem("PREP_START", "추천서 요청", "2026-11-14", true, "RULE"));
    }

    @Test
    void AI_제안이_더_이르면_AI_제안을_쓴다() {
        Card result = prepSchedule.apply(card(List.of(aiPrep("2026-11-12"), DEADLINE),
                List.of(document("CERTIFICATE"))), POSTED);

        assertThat(prepOf(result).at()).isEqualTo("2026-11-12");
        assertThat(prepOf(result).source()).isEqualTo("AI_SUGGESTED");
    }

    @Test
    void 서류가_여러_개면_가장_이른_날_하나만_둔다() {
        Card result = prepSchedule.apply(card(List.of(DEADLINE),
                List.of(document("CERTIFICATE"), document("SELF_INTRODUCTION"), document("APPLICATION_FORM"))), POSTED);

        assertThat(result.schedule()).filteredOn(item -> "PREP_START".equals(item.type())).hasSize(1);
        assertThat(prepOf(result).at()).isEqualTo("2026-11-18");
    }

    @Test
    void 서류가_없으면_마감_3일_전이다() {
        Card result = prepSchedule.apply(card(List.of(DEADLINE), List.of()), POSTED);

        assertThat(prepOf(result)).isEqualTo(new ScheduleItem("PREP_START", "준비 시작", "2026-11-18", true, "RULE"));
    }

    @Test
    void 마감이_없으면_준비_시작을_넣지_않는다() {
        Card result = prepSchedule.apply(card(List.of(aiPrep("2026-11-12")), List.of(document("RECOMMENDATION"))),
                POSTED);

        assertThat(prepOf(result)).isNull();
    }

    @Test
    void 그_밖의_서류는_AI_제안만_쓰고_없으면_넣지_않는다() {
        assertThat(prepOf(prepSchedule.apply(card(List.of(aiPrep("2026-11-15"), DEADLINE),
                List.of(document("OTHER"))), POSTED)).at()).isEqualTo("2026-11-15");
        assertThat(prepOf(prepSchedule.apply(card(List.of(DEADLINE), List.of(document("OTHER"))), POSTED)))
                .isNull();
    }

    @Test
    void 게시일보다_앞이면_게시일로_마감보다_뒤면_마감_하루_전으로_고친다() {
        LocalDate lateNotice = LocalDate.of(2026, 11, 19);
        assertThat(prepOf(prepSchedule.apply(card(List.of(DEADLINE), List.of(document("RECOMMENDATION"))),
                lateNotice)).at()).isEqualTo("2026-11-19");
        assertThat(prepOf(prepSchedule.apply(card(List.of(aiPrep("2026-11-25"), DEADLINE),
                List.of(document("OTHER"))), POSTED)).at()).isEqualTo("2026-11-20");
    }

    @Test
    void 날짜만_있는_마감도_계산한다() {
        ScheduleItem allDayDeadline = new ScheduleItem("APPLY_END", "신청 마감", "2026-11-21", true, "NOTICE");

        assertThat(prepOf(prepSchedule.apply(card(List.of(allDayDeadline), List.of()), POSTED)).at())
                .isEqualTo("2026-11-18");
    }
}
