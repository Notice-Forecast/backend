package com.nf.noticeforecast.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EvalDatasetTest {

    private final EvalDataset dataset = new EvalDataset(JsonMapper.builder().build());

    @Test
    void loadsTwentyLabelsAndThreePersonas() {
        List<EvalLabel> labels = dataset.labels();

        assertThat(labels).hasSize(20);
        assertThat(labels.stream().filter(EvalLabel::full)).hasSize(10);
        assertThat(labels).allSatisfy(label -> assertThat(label.labeler()).isEqualTo("A"));
        assertThat(dataset.personas()).containsOnlyKeys("P1", "P2", "P3");
    }

    /** 학생이 신청할 수 없는 공지(일반대학원 신입생 모집, 신임교원 콜로키엄). 라벨에는 forStudents가 없어 여기 적는다. */
    private static final Set<Long> NOT_FOR_STUDENTS = Set.of(278193L, 278200L);

    /** 라벨 조건을 그대로 카드로 넣으면 사람 판정이 나와야 한다. 판정 엔진과 라벨 규칙(README)이 어긋나면 여기서 드러난다. */
    @Test
    void judgeReproducesHumanVerdictsFromLabelConditions() {
        Map<String, Persona> personas = dataset.personas();
        List<String> mismatches = new ArrayList<>();
        for (EvalLabel label : dataset.labels()) {
            if (!label.full()) {
                continue;
            }
            Card card = new Card("", !NOT_FOR_STUDENTS.contains(label.articleNo()), label.conditions().stream()
                    .map(c -> new CardCondition(c.field(), c.op(), c.values(), "", "본문"))
                    .toList(), List.of(), List.of(), null);
            label.verdicts().forEach((persona, human) -> {
                Verdict judged = ProfileJudge.judge(card, personas.get(persona));
                if (judged != Verdict.valueOf(human)) {
                    mismatches.add(label.articleNo() + " " + persona + ": 라벨 " + human + ", 엔진 " + judged);
                }
            });
        }
        assertThat(mismatches).isEmpty();
    }
}
