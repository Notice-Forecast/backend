package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonalInfoMaskerTest {

    private final PersonalInfoMasker masker = new PersonalInfoMasker();

    @Test
    void 이메일_전화번호_학번을_가린다() {
        String masked = masker.mask("문의: abc.d@catholic.ac.kr, 02-2164-4072, 010 1234 5678, 학번 202312345");

        assertThat(masked).isEqualTo("문의: [가림], [가림], [가림], 학번 [가림]");
    }

    @Test
    void 날짜와_내선번호는_가리지_않는다() {
        String text = "2026. 11. 24.(화) 9시까지, 문의 대외협력팀(4166)";

        assertThat(masker.mask(text)).isEqualTo(text);
    }

    @Test
    void 성씨로_시작하는_세_글자가_다섯_개_이상_이어진_줄은_통째로_뺀다() {
        String masked = masker.mask("합격자 안내\n김민수, 이서연, 박지훈, 최유진, 정하늘\n축하합니다");

        assertThat(masked).isEqualTo("합격자 안내\n[명단 생략]\n축하합니다");
    }

    @Test
    void 일반_안내_문장은_명단으로_보지_않는다() {
        String text = "관정재단에서 2027년도 국내대학 장학생을 아래와 같이 선발하오니, 지원자격 및 제출서류를 확인하여 신청하시기 바랍니다.";

        assertThat(masker.looksLikeNameList(text)).isFalse();
        assertThat(masker.looksLikeNameList("김민수 이서연 박지훈 최유진")).isFalse();
    }

    @Test
    void 명단성_제목을_알아본다() {
        assertThat(masker.isRosterTitle("[기숙사운영센터] 동계방학 기숙사 합격자 발표")).isTrue();
        assertThat(masker.isRosterTitle("장학생 선발 결과 안내")).isTrue();
        assertThat(masker.isRosterTitle("장학생 선발 안내")).isFalse();
    }
}
