package com.nf.noticeforecast.metrics;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.notice.DepartmentNames;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 프론트와 같은 판정(CLAUDE.md 6.3)을 페르소나 전체 프로필로 한다. 판정 정확도 실측에만 쓴다.
 * <ul>
 *   <li>forStudents가 false면 숨김.</li>
 *   <li>조건이 모두 충족이면 해당, 하나라도 확인 불가(MANUAL 포함)면 조건부, 하나라도 불충족이면 숨김. SELF_CHOICE는 뺀다.</li>
 *   <li>학과는 주전공이 맞으면 충족, 추가 전공만 맞으면 확인 불가(조건부), 둘 다 아니면 불충족. NOT_IN은 주전공이 걸리면 불충족,
 *   추가 전공만 걸리면 확인 불가. 이름은 가운뎃점·공백을 빼고 비교.</li>
 *   <li>복학 예정(RETURNING)은 학적 조건에서 휴학생(LEAVE)으로도 본다.</li>
 * </ul>
 */
public final class ProfileJudge {

    enum Result { SATISFIED, UNKNOWN, FAILED }

    private ProfileJudge() {
    }

    public static Verdict judge(Card card, Persona persona) {
        if (Boolean.FALSE.equals(card.forStudents())) {
            return Verdict.HIDDEN;
        }
        boolean unknown = false;
        for (CardCondition condition : card.conditions()) {
            if ("SELF_CHOICE".equals(condition.field())) {
                continue;
            }
            Result result = evaluate(condition.field(), condition.op(), condition.values(), persona);
            if (result == Result.FAILED) {
                return Verdict.HIDDEN;
            }
            unknown |= result == Result.UNKNOWN;
        }
        return unknown ? Verdict.CONDITIONAL : Verdict.ELIGIBLE;
    }

    static Result evaluate(String field, String op, List<String> values, Persona persona) {
        if ("MANUAL".equals(op)) {
            return Result.UNKNOWN;
        }
        return switch (field) {
            case "DEPARTMENT" -> department(op, values, persona);
            case "GRADE" -> persona.grade() == null ? Result.UNKNOWN
                    : number(op, values, BigDecimal.valueOf(persona.grade()));
            case "GPA" -> persona.gpa() == null ? Result.UNKNOWN : number(op, values, BigDecimal.valueOf(persona.gpa()));
            case "ENROLLMENT_STATUS" -> enrollment(op, values, persona.enrollmentStatus());
            case "DORM_RESIDENT" -> flag(op, values, persona.dormResident());
            case "INTERNATIONAL" -> flag(op, values, persona.international());
            default -> Result.UNKNOWN;
        };
    }

    private static Result department(String op, List<String> values, Persona persona) {
        if (persona.department() == null) {
            return Result.UNKNOWN;
        }
        Set<String> names = new HashSet<>();
        values.forEach(value -> names.add(DepartmentNames.normalize(value)));
        boolean main = names.contains(DepartmentNames.normalize(persona.department()));
        boolean extra = persona.extraDepartments().stream().anyMatch(name -> names.contains(DepartmentNames.normalize(name)));
        return switch (op) {
            case "IN" -> main ? Result.SATISFIED : extra ? Result.UNKNOWN : Result.FAILED;
            // 추가 전공만 제외 대상이면 IN과 같이 확인 불가(조건부)로 본다.
            case "NOT_IN" -> main ? Result.FAILED : extra ? Result.UNKNOWN : Result.SATISFIED;
            default -> Result.UNKNOWN;
        };
    }

    private static Result number(String op, List<String> values, BigDecimal mine) {
        List<BigDecimal> numbers = values.stream().map(ProfileJudge::decimal).filter(v -> v != null).toList();
        if (numbers.isEmpty()) {
            return Result.UNKNOWN;
        }
        Boolean ok = switch (op) {
            case "IN" -> numbers.stream().anyMatch(v -> v.compareTo(mine) == 0);
            case "NOT_IN" -> numbers.stream().noneMatch(v -> v.compareTo(mine) == 0);
            case "GTE" -> mine.compareTo(numbers.getFirst()) >= 0;
            case "LTE" -> mine.compareTo(numbers.getFirst()) <= 0;
            default -> null;
        };
        if (ok == null) {
            return Result.UNKNOWN;
        }
        return ok ? Result.SATISFIED : Result.FAILED;
    }

    private static Result enrollment(String op, List<String> values, String status) {
        if (status == null) {
            return Result.UNKNOWN;
        }
        Set<String> mine = new HashSet<>(Set.of(status));
        if ("RETURNING".equals(status)) {
            mine.add("LEAVE");
        }
        boolean any = values.stream().anyMatch(mine::contains);
        return switch (op) {
            case "IN" -> any ? Result.SATISFIED : Result.FAILED;
            case "NOT_IN" -> any ? Result.FAILED : Result.SATISFIED;
            default -> Result.UNKNOWN;
        };
    }

    private static Result flag(String op, List<String> values, Boolean mine) {
        if (mine == null || values.size() != 1) {
            return Result.UNKNOWN;
        }
        boolean matches = values.getFirst().equalsIgnoreCase(mine.toString());
        return switch (op) {
            case "IN" -> matches ? Result.SATISFIED : Result.FAILED;
            case "NOT_IN" -> matches ? Result.FAILED : Result.SATISFIED;
            default -> Result.UNKNOWN;
        };
    }

    static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
