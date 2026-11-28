package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ForecastNoticeRow;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 시드 계열의 다음 게시 시기를 예측한다(CLAUDE.md 10장, 11/28 결정). DB를 모르는 순수 계산이다.
 * <ul>
 *   <li>다음 학기 시작 45일 전부터 올라온 공지는 다음 학기 회차로 본다.</li>
 *   <li>학기마다 첫 게시 한 번만 회차로 센다(재게시·추가 모집 제외).</li>
 *   <li>가중치: 최근 회차 1, 한 회차 앞설 때마다 0.8배. 2021학년도는 0.5배를 곱한다.</li>
 *   <li>예측 기간: 다음 학기 기준일 + 가중평균 m을 중심으로 ±min(7, max(2, s))일.</li>
 *   <li>신뢰도: 2회 이하 LOW, 3회 이상이고 s ≤ 7이면 HIGH, 그 외 MEDIUM.</li>
 * </ul>
 */
public final class ForecastCalculator {

    static final int NEXT_TERM_LEAD_DAYS = 45;
    static final double RECENCY_DECAY = 0.8;
    static final double COVID_YEAR_WEIGHT = 0.5;
    static final long MIN_HALF_WIDTH = 2;
    static final long MAX_HALF_WIDTH = 7;

    private ForecastCalculator() {
    }

    public record Round(String term, LocalDate postedAt, Long noticeId, long offsetDays) {
    }

    public record Prediction(LocalDate expectedFrom, LocalDate expectedTo, ForecastConfidence confidence,
                             String reason, List<Round> past, double meanOffsetDays, double stdDevDays) {
    }

    public static Prediction predict(SeriesAnchor anchor, List<ForecastNoticeRow> notices,
                                     List<TermCalendar> terms, LocalDate today) {
        if (anchor == null || terms.isEmpty()) {
            return empty();
        }
        List<TermCalendar> sortedTerms = terms.stream()
                .sorted(Comparator.comparing(TermCalendar::getStartDate))
                .toList();
        List<Round> rounds = firstRoundPerTerm(anchor, notices, sortedTerms);
        if (rounds.isEmpty()) {
            return empty();
        }

        int count = rounds.size();
        double sumW = 0;
        double sumWX = 0;
        double[] weights = new double[count];
        for (int i = 0; i < count; i++) {
            Round round = rounds.get(i);
            int ageFromLatest = (count - 1) - i;
            double w = Math.pow(RECENCY_DECAY, ageFromLatest);
            if (round.term().startsWith("2021-")) {
                w *= COVID_YEAR_WEIGHT;
            }
            weights[i] = w;
            sumW += w;
            sumWX += w * round.offsetDays();
        }
        double mean = sumWX / sumW;
        double variance = 0;
        for (int i = 0; i < count; i++) {
            double diff = rounds.get(i).offsetDays() - mean;
            variance += weights[i] * diff * diff;
        }
        double stdDev = Math.sqrt(variance / sumW);

        long halfWidth = Math.min(MAX_HALF_WIDTH, Math.max(MIN_HALF_WIDTH, Math.round(stdDev)));
        long roundedMean = Math.round(mean);
        Set<String> postedTerms = rounds.stream().map(Round::term).collect(Collectors.toSet());
        List<TermCalendar> candidates = withEstimatedNextTerms(sortedTerms);
        LocalDate expectedFrom = null;
        LocalDate expectedTo = null;
        for (TermCalendar term : candidates) {
            if (postedTerms.contains(term.getTerm())) {
                continue;
            }
            LocalDate anchorDate = anchorDate(term, anchor);
            if (anchorDate == null) {
                continue;
            }
            LocalDate center = anchorDate.plusDays(roundedMean);
            LocalDate from = center.minusDays(halfWidth);
            LocalDate to = center.plusDays(halfWidth);
            if (!to.isBefore(today)) {
                expectedFrom = from;
                expectedTo = to;
                break;
            }
        }

        ForecastConfidence confidence = count <= 2 ? ForecastConfidence.LOW
                : (stdDev <= MAX_HALF_WIDTH ? ForecastConfidence.HIGH : ForecastConfidence.MEDIUM);
        List<Round> latestFirst = new ArrayList<>(rounds);
        latestFirst.sort(Comparator.comparing(Round::postedAt).reversed().thenComparing(Round::noticeId, Comparator.reverseOrder()));
        return new Prediction(expectedFrom, expectedTo, confidence, reasonText(anchor, count, roundedMean),
                latestFirst, mean, stdDev);
    }

    static List<Round> firstRoundPerTerm(SeriesAnchor anchor, List<ForecastNoticeRow> notices,
                                         List<TermCalendar> sortedTerms) {
        List<ForecastNoticeRow> ordered = notices.stream()
                .sorted(Comparator.comparing(ForecastNoticeRow::postedAt).thenComparing(ForecastNoticeRow::id))
                .toList();
        Map<String, Round> byTerm = new LinkedHashMap<>();
        for (ForecastNoticeRow notice : ordered) {
            LocalDate posted = LocalDate.ofInstant(notice.postedAt(), KoreanTime.ZONE);
            TermCalendar term = assignTerm(posted, sortedTerms);
            if (term == null || byTerm.containsKey(term.getTerm())) {
                continue;
            }
            LocalDate anchorDate = anchorDate(term, anchor);
            if (anchorDate == null) {
                continue;
            }
            long offset = ChronoUnit.DAYS.between(anchorDate, posted);
            byTerm.put(term.getTerm(), new Round(term.getTerm(), posted, notice.id(), offset));
        }
        return new ArrayList<>(byTerm.values());
    }

    /** 다음 학기 시작 45일 전부터는 다음 학기로 본다. 학사일정이 없는 해(2019·2020)는 null. */
    static TermCalendar assignTerm(LocalDate posted, List<TermCalendar> sortedTerms) {
        TermCalendar matched = null;
        for (TermCalendar term : sortedTerms) {
            if (!posted.isBefore(term.getStartDate().minusDays(NEXT_TERM_LEAD_DAYS))) {
                matched = term;
            }
        }
        return matched;
    }

    static LocalDate anchorDate(TermCalendar term, SeriesAnchor anchor) {
        return anchor == SeriesAnchor.TERM_START ? term.getStartDate() : term.getEndDate();
    }

    /** DB에 다음 학기 학사일정이 아직 없으면 작년 같은 학기 날짜에 1년을 더해 추정한다(11/29 결정). */
    static List<TermCalendar> withEstimatedNextTerms(List<TermCalendar> sortedTerms) {
        if (sortedTerms.isEmpty()) {
            return List.of();
        }
        Map<String, TermCalendar> byCode = sortedTerms.stream()
                .collect(Collectors.toMap(TermCalendar::getTerm, Function.identity(), (a, b) -> b));
        List<TermCalendar> extended = new ArrayList<>(sortedTerms);
        String latest = sortedTerms.getLast().getTerm();
        String[] parts = latest.split("-");
        if (parts.length != 2) {
            return extended;
        }
        int year;
        int semester;
        try {
            year = Integer.parseInt(parts[0]);
            semester = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return extended;
        }
        for (int i = 0; i < 2; i++) {
            if (semester == 1) {
                semester = 2;
            } else {
                year++;
                semester = 1;
            }
            String code = year + "-" + semester;
            if (byCode.containsKey(code)) {
                continue;
            }
            TermCalendar prevYear = byCode.get((year - 1) + "-" + semester);
            if (prevYear != null) {
                LocalDate estStart = prevYear.getStartDate().plusYears(1);
                LocalDate estEnd = prevYear.getEndDate() == null ? null : prevYear.getEndDate().plusYears(1);
                TermCalendar estimated = TermCalendar.of(code, estStart, estEnd);
                extended.add(estimated);
                byCode.put(code, estimated);
            }
        }
        return extended;
    }

    static String reasonText(SeriesAnchor anchor, int count, long meanDays) {
        String anchorLabel = anchor == SeriesAnchor.TERM_START ? "학기 시작" : "학기 종료";
        String offsetLabel;
        if (meanDays == 0) {
            offsetLabel = "당일";
        } else if (Math.abs(meanDays) >= 14) {
            long weeks = Math.round(Math.abs(meanDays) / 7.0);
            offsetLabel = weeks + "주 " + (meanDays < 0 ? "전" : "후");
        } else {
            offsetLabel = Math.abs(meanDays) + "일 " + (meanDays < 0 ? "전" : "후");
        }
        if (count == 1) {
            return "지난 회차는 " + anchorLabel + " " + offsetLabel + "에 올라왔어요.";
        }
        return "최근 " + count + "개 학기 평균 " + anchorLabel + " " + offsetLabel + "에 올라왔어요.";
    }

    private static Prediction empty() {
        return new Prediction(null, null, ForecastConfidence.LOW, "예측할 과거 회차가 부족해요.", List.of(), 0, 0);
    }
}
