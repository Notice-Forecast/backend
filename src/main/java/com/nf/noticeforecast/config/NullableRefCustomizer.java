package com.nf.noticeforecast.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * springdoc은 nullable한 $ref를 {"type":"null","$ref":...}로 내보내서 openapi-typescript가 null을 놓친다.
 * anyOf: [$ref, {type: null}] 형태로 바꿔 `T | null`로 생성되게 한다.
 */
@Component
public class NullableRefCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().values().forEach(NullableRefCustomizer::rewriteProperties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void rewriteProperties(Schema schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.replaceAll((name, property) -> isNullableRef(property) ? toAnyOf(property) : property);
    }

    @SuppressWarnings("rawtypes")
    private static boolean isNullableRef(Schema property) {
        if (property.get$ref() == null) {
            return false;
        }
        Set<String> types = property.getTypes();
        return Boolean.TRUE.equals(property.getNullable()) || (types != null && types.contains("null"))
                || "null".equals(property.getType());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema toAnyOf(Schema property) {
        Schema ref = new Schema().$ref(property.get$ref());
        Schema nullType = new Schema();
        nullType.addType("null");
        Schema result = new Schema();
        result.setAnyOf(List.of(ref, nullType));
        result.setDescription(property.getDescription());
        return result;
    }
}
