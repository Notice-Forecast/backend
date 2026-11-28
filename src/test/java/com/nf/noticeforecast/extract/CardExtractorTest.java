package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CardExtractorTest {

    private static final String BODY = "3·4학년 재학생 대상\n신청기간: 2026. 11. 25.(수) ~ 11. 26.(목) 17:00까지";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);
    private static final CardExtractor.Input INPUT = new CardExtractor.Input("장학생 선발", "학생지원팀",
            LocalDate.of(2026, 11, 25), BODY, Map.of());

    private static final String VALID = """
            {"summary":"11/26 17시까지 신청합니다.","forStudents":true,
             "conditions":[{"field":"GRADE","op":"IN","values":["3","4"],"evidence":"3·4학년 재학생","evidenceSource":"본문"}],
             "schedule":[{"type":"APPLY_END","label":"신청 마감","at":"2026-11-26T17:00:00+09:00","allDay":false,"source":"NOTICE"}],
             "documents":[],"howToApply":null}
            """;
    private static final String MADE_UP_EVIDENCE = VALID.replace("3·4학년 재학생\",\"evidenceSource", "3학년 이상\",\"evidenceSource");

    private static class FakeLlm implements LlmClient {
        final Deque<Object> responses = new ArrayDeque<>();
        final List<LlmRequest> requests = new ArrayList<>();

        FakeLlm reply(String text) {
            responses.add(new LlmResult(text, "end_turn", "claude-haiku-5-5", 100, 50));
            return this;
        }

        FakeLlm stop(String stopReason) {
            responses.add(new LlmResult("", stopReason, "claude-haiku-5-5", 100, 0));
            return this;
        }

        FakeLlm fail(RuntimeException e) {
            responses.add(e);
            return this;
        }

        @Override
        public LlmResult complete(LlmRequest request) {
            requests.add(request);
            Object next = responses.removeFirst();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (LlmResult) next;
        }
    }

    private static CardExtractor.Outcome run(FakeLlm llm) {
        return new CardExtractor(llm, JsonMapper.builder().build(), new PrepSchedule(PrepScheduleProperties.defaults())).extract(INPUT, TODAY);
    }

    @Test
    void extractsValidCardOnFirstTry() {
        FakeLlm llm = new FakeLlm().reply(VALID);

        CardExtractor.Outcome outcome = run(llm);

        assertThat(outcome).isInstanceOfSatisfying(CardExtractor.Extracted.class, e -> {
            assertThat(e.attempts()).isEqualTo(1);
            assertThat(e.card().conditions()).hasSize(1);
            assertThat(e.deadline()).isEqualTo(new CardExtractor.Deadline(Instant.parse("2026-11-26T08:00:00Z"), false));
        });
        assertThat(llm.requests.getFirst().system()).contains("2026-11-27").contains("2026-11-25");
    }

    @Test
    void retriesOnceWithValidationErrors() {
        FakeLlm llm = new FakeLlm().reply(MADE_UP_EVIDENCE).reply(VALID);

        CardExtractor.Outcome outcome = run(llm);

        assertThat(outcome).isInstanceOfSatisfying(CardExtractor.Extracted.class,
                e -> assertThat(e.attempts()).isEqualTo(2));
        assertThat(llm.requests.get(1).user()).contains("검사에서 실패").contains("3학년 이상");
    }

    @Test
    void rejectsAfterSecondFailure() {
        FakeLlm llm = new FakeLlm().reply(MADE_UP_EVIDENCE).reply("{not json");

        assertThat(run(llm)).isInstanceOfSatisfying(CardExtractor.Rejected.class,
                r -> assertThat(r.errors()).singleElement().asString().contains("JSON"));
    }

    @Test
    void refusalCountsAsFailedAttempt() {
        FakeLlm llm = new FakeLlm().stop("refusal").reply(VALID);

        assertThat(run(llm)).isInstanceOfSatisfying(CardExtractor.Extracted.class,
                e -> assertThat(e.attempts()).isEqualTo(2));
    }

    @Test
    void unavailableLlmLeavesNoticeForLater() {
        FakeLlm llm = new FakeLlm().fail(new LlmUnavailableException("429", null));

        assertThat(run(llm)).isInstanceOf(CardExtractor.RetryLater.class);
    }

    @Test
    void rejectedRequestDoesNotMarkNotice() {
        FakeLlm llm = new FakeLlm().fail(new LlmRejectedException("400 bad schema", null));

        assertThat(run(llm)).isInstanceOfSatisfying(CardExtractor.RetryLater.class,
                r -> assertThat(r.noticeRejected()).isTrue());
    }

    @Test
    void authFailureStopsBatchInsteadOfSkippingNotice() {
        FakeLlm llm = new FakeLlm().fail(new LlmRejectedException("401 invalid x-api-key", null, 401, false));

        assertThat(run(llm)).isInstanceOfSatisfying(CardExtractor.RetryLater.class,
                r -> assertThat(r.noticeRejected()).isFalse());
    }

    @Test
    void inputTooLongMarksNeedsOriginalWithoutRetry() {
        FakeLlm llm = new FakeLlm().fail(new LlmRejectedException("400 prompt is too long", null, true));

        assertThat(run(llm)).isInstanceOfSatisfying(CardExtractor.Rejected.class, r -> {
            assertThat(r.reason()).isEqualTo(com.nf.noticeforecast.notice.ExtractionReason.ATTACHMENT_UNREADABLE);
            assertThat(r.attempts()).isEqualTo(1);
        });
        assertThat(llm.requests).hasSize(1);
    }

    @Test
    void allDayDeadlineKeepsDateWithoutTime() {
        String allDay = VALID.replace("\"2026-11-26T17:00:00+09:00\",\"allDay\":false", "\"2026-11-26\",\"allDay\":true");

        assertThat(run(new FakeLlm().reply(allDay))).isInstanceOfSatisfying(CardExtractor.Extracted.class,
                e -> assertThat(e.deadline()).isEqualTo(new CardExtractor.Deadline(Instant.parse("2026-11-25T15:00:00Z"), true)));
    }
}
