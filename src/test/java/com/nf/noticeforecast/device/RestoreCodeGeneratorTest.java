package com.nf.noticeforecast.device;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RestoreCodeGeneratorTest {

    @Test
    void 네_자리씩_끊고_헷갈리는_글자를_쓰지_않는다() {
        RestoreCodeGenerator generator = new RestoreCodeGenerator();
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[2-9A-HJ-NP-Z]{4}-[2-9A-HJ-NP-Z]{4}");
        }
    }
}
