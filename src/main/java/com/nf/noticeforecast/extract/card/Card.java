package com.nf.noticeforecast.extract.card;

import java.util.List;

public record Card(
        String summary,
        Boolean forStudents,
        List<CardCondition> conditions,
        List<ScheduleItem> schedule,
        List<CardDocument> documents,
        String howToApply) {

    public Card {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        schedule = schedule == null ? List.of() : List.copyOf(schedule);
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
