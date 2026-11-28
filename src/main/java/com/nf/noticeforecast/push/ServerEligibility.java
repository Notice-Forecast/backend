package com.nf.noticeforecast.push;

import com.nf.noticeforecast.device.Device;
import com.nf.noticeforecast.device.EnrollmentStatus;
import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardCondition;
import com.nf.noticeforecast.notice.DepartmentNames;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 서버 쪽 판정(CLAUDE.md 6.3). 서버는 matchFilter(주전공·추가 전공·학년·학적)만 알아서, "확인할 수 있는 조건 중 불충족이 없음"이면
 * 해당으로 본다. 이 결과는 새 공지 알림 대상을 고를 때만 쓴다. 평점·기숙사생·유학생·직접 확인 조건은 확인 불가로 넘긴다.
 * 복학 예정(RETURNING)은 학적 조건에서 휴학생(LEAVE)으로도 본다(11/28 결정).
 */
public final class ServerEligibility {

    private ServerEligibility() {
    }

    public static boolean notExcluded(Card card, Device device) {
        if (card == null || Boolean.FALSE.equals(card.forStudents())) {
            return false;
        }
        for (CardCondition condition : card.conditions()) {
            if (fails(condition, device)) {
                return false;
            }
        }
        return true;
    }

    static boolean fails(CardCondition condition, Device device) {
        return switch (condition.field()) {
            case "DEPARTMENT" -> failsDepartment(condition, device);
            case "GRADE" -> failsGrade(condition, device.getMatchGrade());
            case "ENROLLMENT_STATUS" -> failsEnrollment(condition, device.getMatchEnrollmentStatus());
            default -> false;
        };
    }

    private static boolean failsDepartment(CardCondition condition, Device device) {
        if (device.getMatchDepartment() == null) {
            return false;
        }
        Set<String> values = normalized(condition.values());
        boolean main = values.contains(DepartmentNames.normalize(device.getMatchDepartment()));
        boolean extra = device.getMatchExtraDepartments().stream()
                .anyMatch(name -> values.contains(DepartmentNames.normalize(name)));
        return switch (condition.op()) {
            // 추가 전공만 맞으면 "조건부"라 불충족은 아니다.
            case "IN" -> !main && !extra;
            case "NOT_IN" -> main;
            default -> false;
        };
    }

    private static boolean failsGrade(CardCondition condition, Integer grade) {
        if (grade == null) {
            return false;
        }
        List<Integer> values = condition.values().stream().map(ServerEligibility::number).filter(v -> v != null).toList();
        if (values.isEmpty()) {
            return false;
        }
        return switch (condition.op()) {
            case "IN" -> !values.contains(grade);
            case "NOT_IN" -> values.contains(grade);
            case "GTE" -> grade < values.getFirst();
            case "LTE" -> grade > values.getFirst();
            default -> false;
        };
    }

    private static boolean failsEnrollment(CardCondition condition, EnrollmentStatus status) {
        if (status == null) {
            return false;
        }
        Set<String> mine = new HashSet<>(Set.of(status.name()));
        if (status == EnrollmentStatus.RETURNING) {
            mine.add(EnrollmentStatus.LEAVE.name());
        }
        Set<String> values = new HashSet<>(condition.values());
        boolean any = mine.stream().anyMatch(values::contains);
        return switch (condition.op()) {
            case "IN" -> !any;
            case "NOT_IN" -> any;
            default -> false;
        };
    }

    private static Set<String> normalized(List<String> names) {
        Set<String> result = new HashSet<>();
        names.forEach(name -> result.add(DepartmentNames.normalize(name)));
        return result;
    }

    private static Integer number(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
