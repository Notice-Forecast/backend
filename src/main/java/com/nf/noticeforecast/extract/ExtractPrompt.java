package com.nf.noticeforecast.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

/** 프롬프트 본문은 resources/prompts/extract-card.md에 둔다(CLAUDE.md 8.3). */
final class ExtractPrompt {

    private static final String TEMPLATE = load();

    private ExtractPrompt() {
    }

    static String system(LocalDate today, LocalDate postedAt) {
        return TEMPLATE.replace("{{today}}", today.toString()).replace("{{postedAt}}", postedAt.toString());
    }

    static String user(String title, String department, LocalDate postedAt, String bodyText) {
        return "제목: " + title + "\n부서: " + (department == null ? "" : department) + "\n게시일: " + postedAt
                + "\n\n본문:\n" + bodyText;
    }

    static String retry(String user, List<String> errors) {
        return user + "\n\n---\n이전에 만든 카드가 아래 검사에서 실패했다. 고쳐서 카드를 다시 만들어라.\n- "
                + String.join("\n- ", errors);
    }

    private static String load() {
        try {
            return new ClassPathResource("prompts/extract-card.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
