package com.nf.noticeforecast.metrics;

import java.util.List;
import java.util.Map;

/**
 * 정답셋 공지 하나(CLAUDE.md 15.1, eval/README.md). FULL은 마감·대상·조건·서류·판정, BASIC은 마감·대상만 적는다.
 *
 * @param deadline 신청 마감. 날짜만 있으면 YYYY-MM-DD, 시각까지 있으면 +09:00 시각. 없으면 null
 */
public record EvalLabel(String source, Long articleNo, String labeler, String scope, String deadline, String target,
                        List<Condition> conditions, List<String> documents, Map<String, String> verdicts) {

    public static final String FULL = "FULL";

    public EvalLabel {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        documents = documents == null ? List.of() : List.copyOf(documents);
        verdicts = verdicts == null ? Map.of() : Map.copyOf(verdicts);
    }

    public boolean full() {
        return FULL.equals(scope);
    }

    public record Condition(String field, String op, List<String> values) {

        public Condition {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }
}
