package com.nf.noticeforecast.metrics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * jar에 함께 실은 정답셋(eval/notices/*.json)과 페르소나(eval/personas.json)를 읽는다. 원본은 src/test/resources/eval이고
 * build.gradle의 processResources가 같은 경로로 복사한다(CLAUDE.md 15.1·15.2).
 */
@Component
@RequiredArgsConstructor
public class EvalDataset {

    static final String LABELS = "classpath*:eval/notices/*.json";
    static final String PERSONAS = "eval/personas.json";

    private final JsonMapper jsonMapper;
    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public List<EvalLabel> labels() {
        try {
            Map<Long, EvalLabel> byArticle = new LinkedHashMap<>();
            for (Resource resource : resolver.getResources(LABELS)) {
                try (InputStream in = resource.getInputStream()) {
                    EvalLabel label = jsonMapper.readValue(in, EvalLabel.class);
                    byArticle.putIfAbsent(label.articleNo(), label);
                }
            }
            return byArticle.values().stream().sorted(Comparator.comparing(EvalLabel::articleNo)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Map<String, Persona> personas() {
        try (InputStream in = resolver.getResource("classpath:" + PERSONAS).getInputStream()) {
            return jsonMapper.readValue(in, new TypeReference<Map<String, Persona>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
