package com.nf.noticeforecast.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import org.junit.jupiter.api.Test;

class NullableRefCustomizerTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void nullable_ref를_anyOf로_바꾼다() {
        Schema matchFilter = new Schema().$ref("#/components/schemas/MatchFilter").description("설정 전에는 null");
        matchFilter.addType("null");
        Schema deviceId = new Schema();
        deviceId.addType("string");
        Schema response = new Schema().addProperty("matchFilter", matchFilter).addProperty("deviceId", deviceId);
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("DeviceResponse", response));

        new NullableRefCustomizer().customise(openApi);

        Schema rewritten = (Schema) response.getProperties().get("matchFilter");
        List<Schema> anyOf = rewritten.getAnyOf();
        assertThat(rewritten.get$ref()).isNull();
        assertThat(anyOf).hasSize(2);
        assertThat(anyOf.get(0).get$ref()).isEqualTo("#/components/schemas/MatchFilter");
        assertThat(anyOf.get(1).getTypes()).containsExactly("null");
        assertThat(rewritten.getDescription()).isEqualTo("설정 전에는 null");
        assertThat(response.getProperties().get("deviceId")).isSameAs(deviceId);
    }
}
