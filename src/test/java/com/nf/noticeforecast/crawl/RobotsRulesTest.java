package com.nf.noticeforecast.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RobotsRulesTest {

    @Test
    void 학교_robots의_제한_경로만_막는다() {
        RobotsRules rules = RobotsRules.parse("User-agent: *\nDisallow: /_attach/\nDisallow: /_custom/\nDisallow: /_res/\n");

        assertThat(rules.allows("/ko/campuslife/notice.do")).isTrue();
        assertThat(rules.allows("/_attach/cuk/editor-image/a.png")).isFalse();
        assertThat(rules.allows("/_res/cuk/x.css")).isFalse();
    }

    @Test
    void 다른_봇에게만_적용되는_규칙은_무시한다() {
        RobotsRules rules = RobotsRules.parse("User-agent: OtherBot\nDisallow: /\n\nUser-agent: *\nDisallow: /private\n");

        assertThat(rules.allows("/ko/campuslife/notice.do")).isTrue();
        assertThat(rules.allows("/private/a")).isFalse();
    }

    @Test
    void 전체_금지면_건너뛴다() {
        assertThat(RobotsRules.parse("User-agent: *\nDisallow: /").allows("/ko")).isFalse();
        assertThat(RobotsRules.denyAll().allows("/")).isFalse();
    }
}
