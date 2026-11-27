package com.nf.noticeforecast.extract.card;

import java.util.List;

public record CardCondition(String field, String op, List<String> values, String evidence, String evidenceSource) {

    public CardCondition {
        values = values == null ? List.of() : List.copyOf(values);
    }
}
