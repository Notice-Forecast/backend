package com.nf.noticeforecast.extract.card;

public record ScheduleItem(String type, String label, String at, Boolean allDay, String source) {
}
