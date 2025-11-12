package com.healthassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
class SpringdocConfig {

    @Bean
    OpenApiCustomizer removeAllOfFromPayloads() {
        return openApi -> {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }

            schemas.forEach((name, schema) -> {
                if (name.contains("Payload") && schema.getAllOf() != null && !schema.getAllOf().isEmpty()) {
                    Schema actualSchema = (Schema) schema.getAllOf().get(schema.getAllOf().size() - 1);
                    if (actualSchema.getProperties() != null) {
                        schema.setProperties(actualSchema.getProperties());
                        schema.setRequired(actualSchema.getRequired());
                        schema.setDescription(actualSchema.getDescription());
                        schema.setExample(actualSchema.getExample());
                        schema.getAllOf().clear();
                    }
                }
            });
        };
    }
}

