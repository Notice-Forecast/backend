package com.nf.noticeforecast.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SeriesTitlesTest {

    private static double similarity(String a, String b) {
        return SeriesTitles.similarity(SeriesTitles.bigrams(SeriesTitles.normalize(a)),
                SeriesTitles.bigrams(SeriesTitles.normalize(b)));
    }

    @Test
    void 연도_학기_차수_괄호_외국어를_지운다() {
        assertThat(SeriesTitles.normalize("[학생지원팀](한국장학재단) 2026학년도 1학기 국가근로장학금 2차 신청 안내(2/3~3/17)"))
                .isEqualTo("국가근로장학금 신청 안내");
        assertThat(SeriesTitles.normalize("25-2학기 장학조교(근로 B) 모집")).isEqualTo("장학조교 모집");
        assertThat(SeriesTitles.normalize("[기숙사운영팀] 2025학년도 동계방학 기숙사 추가모집 공고_휴학생 가능 Notice 2025年寒假"))
                .isEqualTo("동계방학 기숙사 추가모집 공고 휴학생 가능");
        assertThat(SeriesTitles.normalize("2019-2 가톨릭대학교 Programming 대회 안내")).isEqualTo("가톨릭대학교 대회 안내");
    }

    @Test
    void 같은_공지의_다른_회차는_비슷하고_다른_공지는_덜_비슷하다() {
        assertThat(similarity("25-1학기 장학조교(근로 B) 모집", "2026-1학기 장학조교(근로 B) 모집")).isEqualTo(1.0);
        assertThat(similarity("[학생지원팀] 2025학년도 교내 근로장학생 모집", "[학생지원팀] 2026학년도 교내 근로장학생 추가 모집"))
                .isGreaterThanOrEqualTo(SeriesTitles.SAME_DEPARTMENT_THRESHOLD);
        assertThat(similarity("장학조교 모집", "국가근로장학금 신청 안내"))
                .isLessThan(SeriesTitles.SAME_DEPARTMENT_THRESHOLD);
    }

    @Test
    void 부서_비교는_괄호와_공백과_가운뎃점을_뺀다() {
        assertThat(SeriesTitles.departmentKey("평생교육원운영팀(성심)")).isEqualTo("평생교육원운영팀");
        assertThat(SeriesTitles.departmentKey("공간디자인ㆍ소비자학과"))
                .isEqualTo(SeriesTitles.departmentKey("공간디자인·소비자학과"));
        assertThat(SeriesTitles.departmentKey(null)).isEmpty();
    }

    @Test
    void 제목_대부분이_대괄호_안이면_태그_글자를_살리고_너무_짧으면_후보를_찾지_않는다() {
        assertThat(SeriesTitles.normalize("[학생 참가 컨테스트 2022 CPWC] 안내")).isEqualTo("학생 참가 컨테스트 안내");
        assertThat(SeriesTitles.normalize("[국제교류팀] 2026-1 Overseas Exchange Fair 안내")).isEqualTo("국제교류팀 안내");
        assertThat(SeriesTitles.tooShort(SeriesTitles.normalize("2026-1 Overseas Exchange Fair 안내"))).isTrue();
        assertThat(SeriesTitles.tooShort(SeriesTitles.normalize("25-1학기 장학조교 모집"))).isFalse();
    }
}
