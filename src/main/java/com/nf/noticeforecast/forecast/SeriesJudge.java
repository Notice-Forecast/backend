package com.nf.noticeforecast.forecast;

import com.nf.noticeforecast.extract.LlmClient;
import com.nf.noticeforecast.extract.LlmRequest;
import com.nf.noticeforecast.extract.LlmResult;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.NoticeTitleRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM에게 "새 공지가 후보 공지와 같은 공지의 다른 회차인가"를 후보마다 yes/no와 이유로 묻는다(CLAUDE.md 8.4).
 * 프롬프트는 resources/prompts/judge-series.md에 둔다.
 */
@Component
@RequiredArgsConstructor
public class SeriesJudge {

    private static final String SYSTEM = load();
    private static final Map<String, Object> SCHEMA = schema();

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;

    public record Verdict(int candidate, boolean same, String reason) {
    }

    public record Answer(List<Verdict> judgments) {
    }

    public record Result(List<Verdict> verdicts, String model) {
    }

    /**
     * @throws com.nf.noticeforecast.extract.LlmUnavailableException 나중에 다시 시도할 오류
     * @throws com.nf.noticeforecast.extract.LlmRejectedException    요청이 거절된 오류
     * @throws IllegalStateException                                  응답을 읽을 수 없을 때
     */
    public Result judge(NoticeTitleRow notice, List<NoticeTitleRow> candidates) {
        StringBuilder user = new StringBuilder("새 공지\n").append(describe(notice)).append("\n\n후보\n");
        for (int i = 0; i < candidates.size(); i++) {
            user.append(i + 1).append(". ").append(describe(candidates.get(i))).append('\n');
        }
        user.append("\n후보마다 candidate 번호(1부터)로 judgments를 하나씩 채워라.");
        LlmResult result = llmClient.complete(new LlmRequest(SYSTEM, user.toString(), SCHEMA));
        if (!"end_turn".equals(result.stopReason())) {
            throw new IllegalStateException("stop_reason=" + result.stopReason());
        }
        try {
            Answer answer = jsonMapper.readValue(result.text(), Answer.class);
            List<Verdict> verdicts = answer.judgments() == null ? List.of() : answer.judgments().stream()
                    .filter(v -> v.candidate() >= 1 && v.candidate() <= candidates.size())
                    .toList();
            return new Result(verdicts, result.model());
        } catch (JacksonException e) {
            throw new IllegalStateException("JSON을 읽을 수 없습니다: " + e.getOriginalMessage(), e);
        }
    }

    private static String describe(NoticeTitleRow row) {
        LocalDate posted = LocalDate.ofInstant(row.postedAt(), KoreanTime.ZONE);
        return "제목: " + row.title() + " / 부서: " + (row.department() == null ? "" : row.department())
                + " / 게시일: " + posted;
    }

    private static Map<String, Object> schema() {
        Map<String, Object> verdictProperties = new LinkedHashMap<>();
        verdictProperties.put("candidate", Map.of("type", "integer"));
        verdictProperties.put("same", Map.of("type", "boolean"));
        verdictProperties.put("reason", Map.of("type", "string"));
        Map<String, Object> verdict = new LinkedHashMap<>();
        verdict.put("type", "object");
        verdict.put("properties", verdictProperties);
        verdict.put("required", new ArrayList<>(verdictProperties.keySet()));
        verdict.put("additionalProperties", false);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", Map.of("judgments", Map.of("type", "array", "items", verdict)));
        root.put("required", List.of("judgments"));
        root.put("additionalProperties", false);
        return root;
    }

    private static String load() {
        try {
            return new ClassPathResource("prompts/judge-series.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
