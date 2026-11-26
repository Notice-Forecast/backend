package com.nf.noticeforecast.crawl;

import com.nf.noticeforecast.global.time.KoreanTime;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 본문이 이미지뿐인 공지의 제목에서 마감일을 찾는다(CLAUDE.md 8.1).
 * 연도나 시각이 적혀 있지 않으면 추측하지 않고 비운다.
 */
public class TitleDeadlineParser {

    private static final String DATE = "(?:(\\d{4})\\s*\\.\\s*)?(\\d{1,2})\\s*[./]\\s*(\\d{1,2})\\s*\\.?"
            + "|(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일";
    private static final String WEEKDAY = "(?:\\s*\\([월화수목금토일]\\))?";
    private static final String TIME = "(?:\\s*(\\d{1,2})\\s*(?::\\s*(\\d{2})|시(?:\\s*(\\d{1,2})\\s*분)?))?";
    private static final Pattern AFTER_TILDE = Pattern.compile("~\\s*(?:" + DATE + ")" + WEEKDAY + TIME);
    private static final Pattern BEFORE_UNTIL = Pattern.compile("(?:" + DATE + ")" + WEEKDAY + TIME + "\\s*까지");

    public Optional<Instant> parse(String title) {
        for (Pattern pattern : new Pattern[]{AFTER_TILDE, BEFORE_UNTIL}) {
            Matcher matcher = pattern.matcher(title);
            Optional<Instant> found = Optional.empty();
            while (matcher.find()) {
                found = toInstant(matcher);
            }
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Optional<Instant> toInstant(Matcher m) {
        try {
            if (m.group(1) == null || m.group(6) == null) {
                return Optional.empty();
            }
            LocalDate date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    Integer.parseInt(m.group(3)));
            int minute = m.group(7) != null ? Integer.parseInt(m.group(7))
                    : m.group(8) != null ? Integer.parseInt(m.group(8)) : 0;
            LocalTime time = LocalTime.of(Integer.parseInt(m.group(6)), minute);
            return Optional.of(date.atTime(time).atZone(KoreanTime.ZONE).toInstant());
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
    }
}
