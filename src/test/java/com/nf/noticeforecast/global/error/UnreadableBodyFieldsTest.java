package com.nf.noticeforecast.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.nf.noticeforecast.device.dto.DeviceSettingsRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UnreadableBodyFieldsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private Map<String, String> fieldsFor(String json) {
        try {
            mapper.readValue(json, DeviceSettingsRequest.class);
        } catch (RuntimeException ex) {
            return UnreadableBodyFields.from(ex);
        }
        throw new AssertionError("파싱 오류가 나야 합니다.");
    }

    @Test
    void 잘못된_enum_값은_필드_경로와_허용값을_준다() {
        Map<String, String> fields = fieldsFor("""
                {"matchFilter": {"department": "a", "extraDepartments": [], "grade": 3, "enrollmentStatus": "GRADUATE_STUDENT"}}
                """);

        assertThat(fields).containsEntry("matchFilter.enrollmentStatus", "ENROLLED, LEAVE, RETURNING 중 하나여야 합니다.");
    }

    @Test
    void 타입이_틀리면_필드_경로를_준다() {
        Map<String, String> fields = fieldsFor("""
                {"matchFilter": {"grade": "셋"}}
                """);

        assertThat(fields).containsEntry("matchFilter.grade", "형식이 올바르지 않습니다.");
    }

    @Test
    void 깨진_JSON은_빈_fields() {
        assertThat(fieldsFor("{\"matchFilter\": ")).isEmpty();
    }
}
