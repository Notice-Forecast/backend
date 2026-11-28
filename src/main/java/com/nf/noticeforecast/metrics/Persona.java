package com.nf.noticeforecast.metrics;

import java.util.List;

/** 판정 정확도용 페르소나(eval/personas.json, CLAUDE.md 15.1). 프론트 프로필과 같은 항목이다. 이수 학기는 없다. */
public record Persona(String department, List<String> extraDepartments, Integer grade, String enrollmentStatus,
                      Double gpa, Boolean dormResident, Boolean international) {

    public Persona {
        extraDepartments = extraDepartments == null ? List.of() : List.copyOf(extraDepartments);
    }
}
