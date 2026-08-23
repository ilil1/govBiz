package io.basearchitecture.core.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/** 공개 JSON 요청에서 값의 타입을 조용히 바꾸지 않도록 Jackson coercion을 제한합니다. */
@Configuration
public class JsonDeserializationConfig {

    @Bean
    JsonMapperBuilderCustomizer strictJsonRequestTypes() {
        return builder -> {
            reject(builder, LogicalType.Textual,
                    CoercionInputShape.Integer,
                    CoercionInputShape.Float,
                    CoercionInputShape.Boolean);
            reject(builder, LogicalType.Integer,
                    CoercionInputShape.String,
                    CoercionInputShape.Float,
                    CoercionInputShape.Boolean,
                    CoercionInputShape.EmptyString);
            reject(builder, LogicalType.Enum,
                    CoercionInputShape.Integer,
                    CoercionInputShape.Float,
                    CoercionInputShape.Boolean,
                    CoercionInputShape.EmptyString);
            reject(builder, LogicalType.DateTime,
                    CoercionInputShape.Integer,
                    CoercionInputShape.Float,
                    CoercionInputShape.Boolean,
                    CoercionInputShape.EmptyString);
        };
    }

    private static void reject(
            tools.jackson.databind.json.JsonMapper.Builder builder,
            LogicalType logicalType,
            CoercionInputShape... inputShapes
    ) {
        builder.withCoercionConfig(logicalType, coercionConfig -> {
            for (CoercionInputShape inputShape : inputShapes) {
                coercionConfig.setCoercion(inputShape, CoercionAction.Fail);
            }
        });
    }
}
