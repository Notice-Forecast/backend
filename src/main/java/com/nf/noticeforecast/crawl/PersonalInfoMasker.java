package com.nf.noticeforecast.crawl;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 개인정보 마스킹 규칙을 한곳에 모은다(CLAUDE.md 8.2).
 */
public class PersonalInfoMasker {

    static final String HIDDEN = "[가림]";
    static final String ROSTER_OMITTED = "[명단 생략]";

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern RESIDENT_NUMBER = Pattern.compile("(?<!\\d)\\d{6}\\s*-?\\s*[1-8]\\d{6}(?!\\d)");
    private static final Pattern MOBILE_WITHOUT_SEPARATOR = Pattern.compile("(?<!\\d)01[016789]\\d{8}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)0\\d{1,2}[-.)\\s]\\d{3,4}[-.\\s]\\d{4}(?!\\d)");
    private static final Pattern STUDENT_NUMBER = Pattern.compile("(?<!\\d)\\d{8,10}(?!\\d)");
    private static final Pattern TOKEN_SEPARATOR = Pattern.compile("[,·/\\s]+");
    private static final Pattern THREE_HANGUL = Pattern.compile("[가-힣]{3}");
    private static final int ROSTER_MIN_NAMES = 5;

    private static final Set<Character> SURNAMES = "김이박최정강조윤장임한오서신권황안송류전홍고문양손배백허유남심노하곽성차주우구민진나지엄채원천방공현함변염여추도소석선설마길연위표명기반왕금옥육인맹제모탁국어은편용예경봉사부가복태목형피두감음빈동온호범좌팽승간상갈단견당화창"
            .chars().mapToObj(c -> (char) c).collect(Collectors.toUnmodifiableSet());

    private static final List<String> ROSTER_TITLE_WORDS = List.of("합격자", "수상자", "명단", "선발 결과", "선발결과");

    public boolean isRosterTitle(String title) {
        return ROSTER_TITLE_WORDS.stream().anyMatch(title::contains);
    }

    public String mask(String text) {
        return text.lines()
                .map(line -> looksLikeNameList(line) ? ROSTER_OMITTED : maskContacts(line))
                .collect(Collectors.joining("\n"));
    }

    private String maskContacts(String line) {
        String masked = EMAIL.matcher(line).replaceAll(HIDDEN);
        masked = RESIDENT_NUMBER.matcher(masked).replaceAll(HIDDEN);
        masked = MOBILE_WITHOUT_SEPARATOR.matcher(masked).replaceAll(HIDDEN);
        masked = PHONE.matcher(masked).replaceAll(HIDDEN);
        return STUDENT_NUMBER.matcher(masked).replaceAll(HIDDEN);
    }

    boolean looksLikeNameList(String line) {
        int run = 0;
        for (String token : TOKEN_SEPARATOR.split(line.strip())) {
            if (THREE_HANGUL.matcher(token).matches() && SURNAMES.contains(token.charAt(0))) {
                run++;
                if (run >= ROSTER_MIN_NAMES) {
                    return true;
                }
            } else {
                run = 0;
            }
        }
        return false;
    }
}
