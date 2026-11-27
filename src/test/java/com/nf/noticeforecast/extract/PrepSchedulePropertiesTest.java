package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class PrepSchedulePropertiesTest {

    @Test
    void application_yml의_서류_소요일_표를_그대로_읽는다() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        PrepScheduleProperties properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("app.prep", PrepScheduleProperties.class).get();

        assertThat(properties.leadDays()).containsEntry("RECOMMENDATION", 7).containsEntry("CERTIFICATE", 1)
                .containsEntry("SELF_INTRODUCTION", 3).containsEntry("APPLICATION_FORM", 1).hasSize(4);
        assertThat(properties.noDocumentDays()).isEqualTo(3);
    }
}
