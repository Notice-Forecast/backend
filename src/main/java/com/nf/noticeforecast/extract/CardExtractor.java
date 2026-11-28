package com.nf.noticeforecast.extract;

import com.nf.noticeforecast.extract.card.Card;
import com.nf.noticeforecast.extract.card.CardSchema;
import com.nf.noticeforecast.extract.card.CardValidator;
import com.nf.noticeforecast.extract.card.ScheduleItem;
import com.nf.noticeforecast.global.time.KoreanTime;
import com.nf.noticeforecast.notice.ExtractionReason;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공지 하나를 카드로 바꾼다. 검증에 실패하면 오류를 붙여 한 번 더 시도하고, 그래도 실패하면 원문 확인 필요로 돌린다(CLAUDE.md 8.3).
 * 네트워크·429·5xx는 상태를 바꾸지 않고 다음 배치에서 다시 시도하도록 RetryLater를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardExtractor {

    private static final int MAX_ATTEMPTS = 2;

    private final LlmClient llmClient;
    private final JsonMapper jsonMapper;
    private final PrepSchedule prepSchedule;

    public record Input(String title, String department, LocalDate postedAt, String bodyText,
                        Map<String, String> attachmentTexts) {
    }

    public sealed interface Outcome permits Extracted, Rejected, RetryLater {
    }

    /** @param deadline 카드의 APPLY_END. 없으면 null */
    public record Extracted(Card card, Deadline deadline, String model, int attempts) implements Outcome {
    }

    /** @param allDay 시각 없이 날짜만 있는 마감. 이때 at은 그날 00:00 KST */
    public record Deadline(Instant at, boolean allDay) {
    }

    public record Rejected(List<String> errors, int attempts, ExtractionReason reason) implements Outcome {

        public Rejected(List<String> errors, int attempts) {
            this(errors, attempts, ExtractionReason.VALIDATION_FAILED);
        }
    }

    /** @param noticeRejected 이 공지 요청만 400으로 거절됐다. 다른 공지는 계속 처리할 수 있다 */
    public record RetryLater(String message, boolean noticeRejected) implements Outcome {

        public RetryLater(String message) {
            this(message, false);
        }
    }

    public Outcome extract(Input input, LocalDate today) {
        String system = ExtractPrompt.system(today, input.postedAt());
        String user = ExtractPrompt.user(input.title(), input.department(), input.postedAt(), input.bodyText(),
                input.attachmentTexts());
        List<String> errors = List.of();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String message = attempt == 1 ? user : ExtractPrompt.retry(user, errors);
            LlmResult result;
            try {
                result = llmClient.complete(new LlmRequest(system, message, CardSchema.schema()));
            } catch (LlmUnavailableException e) {
                log.warn("llm unavailable: {}", e.getMessage());
                return new RetryLater(e.getMessage());
            } catch (LlmRejectedException e) {
                if (e.isInputTooLong()) {
                    log.warn("llm input too long: {}", e.getMessage());
                    return new Rejected(List.of("공지가 너무 길어 AI가 읽지 못했습니다."), attempt,
                            ExtractionReason.ATTACHMENT_UNREADABLE);
                }
                // 그 밖의 400·401·403은 공지 상태를 바꾸지 않는다. 401·403은 배치를 멈추고, 400은 이 공지만 건너뛴다.
                log.error("llm rejected request: {}", e.getMessage());
                return new RetryLater(e.getMessage(), !e.isAuthFailure());
            }
            log.info("llm {} attempt {} stop={} in={} out={}", result.model(), attempt, result.stopReason(),
                    result.inputTokens(), result.outputTokens());
            if (!"end_turn".equals(result.stopReason())) {
                errors = List.of("응답이 끝까지 오지 않았습니다(stop_reason=" + result.stopReason() + ").");
                continue;
            }
            Card card;
            try {
                card = jsonMapper.readValue(result.text(), Card.class);
            } catch (JacksonException e) {
                errors = List.of("JSON을 읽을 수 없습니다: " + e.getOriginalMessage());
                continue;
            }
            errors = CardValidator.validate(card, input.bodyText() == null ? "" : input.bodyText(),
                    input.attachmentTexts());
            if (errors.isEmpty()) {
                Card scheduled = prepSchedule.apply(card, input.postedAt());
                return new Extracted(scheduled, deadlineOf(scheduled), result.model(), attempt);
            }
            log.info("card validation failed (attempt {}): {}", attempt, errors);
        }
        return new Rejected(errors, MAX_ATTEMPTS);
    }

    static Deadline deadlineOf(Card card) {
        return card.schedule().stream()
                .filter(item -> "APPLY_END".equals(item.type()))
                .findFirst()
                .map(CardExtractor::toDeadline)
                .orElse(null);
    }

    private static Deadline toDeadline(ScheduleItem item) {
        if (Boolean.TRUE.equals(item.allDay())) {
            return new Deadline(LocalDate.parse(item.at()).atStartOfDay(KoreanTime.ZONE).toInstant(), true);
        }
        return new Deadline(OffsetDateTime.parse(item.at()).toInstant(), false);
    }
}
