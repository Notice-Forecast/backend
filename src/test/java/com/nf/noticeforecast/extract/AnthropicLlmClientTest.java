package com.nf.noticeforecast.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.extract.card.CardSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AnthropicLlmClientTest {

    private final AnthropicLlmClient client = new AnthropicLlmClient(
            new LlmProperties("key", "claude-sonnet-5-5", "medium", 16000), JsonMapper.builder().build());

    @Test
    @SuppressWarnings("unchecked")
    void requestUsesStructuredOutputWithoutForcedTool() {
        Map<String, Object> body = client.requestBody(new LlmRequest("system", "user", CardSchema.schema()));

        assertThat(body).containsEntry("model", "claude-sonnet-5-5").containsEntry("max_tokens", 16000)
                .doesNotContainKeys("tools", "tool_choice", "temperature", "thinking");
        Map<String, Object> outputConfig = (Map<String, Object>) body.get("output_config");
        assertThat(outputConfig).containsEntry("effort", "medium");
        assertThat((Map<String, Object>) outputConfig.get("format")).containsEntry("type", "json_schema");
    }

    @Test
    void parsesTextBlocksAndUsage() {
        String response = """
                {"model":"claude-haiku-5-5","stop_reason":"end_turn",
                 "content":[{"type":"thinking","thinking":""},{"type":"text","text":"{\\"summary\\":"},{"type":"text","text":"\\"x\\"}"}],
                 "usage":{"input_tokens":1200,"output_tokens":340}}
                """;

        LlmResult result = client.parse(response);

        assertThat(result.text()).isEqualTo("{\"summary\":\"x\"}");
        assertThat(result.stopReason()).isEqualTo("end_turn");
        assertThat(result.inputTokens()).isEqualTo(1200);
        assertThat(result.outputTokens()).isEqualTo(340);
    }

    @Test
    void schemaClosesEveryObject() {
        assertThat(CardSchema.schema()).containsEntry("additionalProperties", false);
        assertThat(CardSchema.schema().get("required").toString()).contains("forStudents");
    }
}
