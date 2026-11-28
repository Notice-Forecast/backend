package com.nf.noticeforecast.forecast;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 계열 후보를 고르기 위한 제목 정규화와 유사도(CLAUDE.md 8.4, 11/28 결정).
 * 연도·학기·차수·괄호 표기를 지우고, 한글만 남겨 두 글자씩 끊은 조각(bigram)이 겹치는 비율(Jaccard)로 잰다.
 * 기숙사 공지처럼 제목 뒤에 외국어 번역이 붙는 경우가 있어 한글만 남긴다.
 */
public final class SeriesTitles {

    /** 같은 부서면 이 이상을 후보로 본다(11/28 결정). */
    public static final double SAME_DEPARTMENT_THRESHOLD = 0.5;
    /** 부서가 달라도(재게시) 이 이상이면 후보로 본다(11/28 결정). */
    public static final double OTHER_DEPARTMENT_THRESHOLD = 0.8;

    /** 한글 4글자 미만이면 비교하기에 너무 짧다고 본다. */
    static final int MIN_KEY_LENGTH = 4;

    private static final Pattern SQUARE = Pattern.compile("\\[[^\\]]*]");
    private static final Pattern ROUND = Pattern.compile("\\([^()]*\\)");
    private static final Pattern TERM = Pattern.compile("\\d+\\s*(?:-\\s*\\d+\\s*)?학기");
    private static final Pattern YEAR = Pattern.compile("\\d{2,4}\\s*(?:학년도|년도|년|학년)?");
    private static final Pattern ROUND_NO = Pattern.compile("(?:제\\s*)?\\d+\\s*(?:차|회)");
    private static final Pattern NOT_HANGUL = Pattern.compile("[^가-힣\\s]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SeriesTitles() {
    }

    /** 계열 이름과 비교에 쓰는 정규화 제목. 예: "[학생지원팀] 2026학년도 1학기 국가근로장학금 2차 신청 안내(2/3~3/17)" → "국가근로장학금 신청 안내" */
    public static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String withoutTags = normalize(title, true);
        // 제목 대부분이 [ ] 안에 있으면(예: "[학생 참가 컨테스트 2022 CPWC] 안내") 태그를 지우면 "안내"만 남으므로 태그 안 글자를 살린다.
        return compactLength(withoutTags) >= MIN_KEY_LENGTH ? withoutTags : normalize(title, false);
    }

    /** 정규화 제목이 이보다 짧으면(예: "안내") 아무 공지와도 비슷해 보여서 후보를 찾지 않는다. */
    public static boolean tooShort(String normalized) {
        return compactLength(normalized) < MIN_KEY_LENGTH;
    }

    private static int compactLength(String normalized) {
        return normalized.replace(" ", "").length();
    }

    private static String normalize(String title, boolean removeTags) {
        String text = removeTags ? SQUARE.matcher(title).replaceAll(" ") : title.replace('[', ' ').replace(']', ' ');
        String previous;
        do {
            previous = text;
            text = ROUND.matcher(text).replaceAll(" ");
        } while (!text.equals(previous));
        text = TERM.matcher(text).replaceAll(" ");
        text = ROUND_NO.matcher(text).replaceAll(" ");
        text = YEAR.matcher(text).replaceAll(" ");
        text = NOT_HANGUL.matcher(text).replaceAll(" ");
        return SPACES.matcher(text).replaceAll(" ").strip();
    }

    public static Set<String> bigrams(String normalized) {
        String compact = normalized.replace(" ", "");
        Set<String> grams = new HashSet<>();
        for (int i = 0; i + 2 <= compact.length(); i++) {
            grams.add(compact.substring(i, i + 2));
        }
        if (grams.isEmpty() && !compact.isEmpty()) {
            grams.add(compact);
        }
        return grams;
    }

    public static double similarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int common = 0;
        for (String gram : a) {
            if (b.contains(gram)) {
                common++;
            }
        }
        return (double) common / (a.size() + b.size() - common);
    }

    /** 부서 비교용. 공백과 괄호 안 표기를 뺀다. 예: "평생교육원운영팀(성심)" → "평생교육원운영팀" */
    public static String departmentKey(String department) {
        if (department == null) {
            return "";
        }
        return ROUND.matcher(department).replaceAll("").replaceAll("\\s+", "");
    }
}
