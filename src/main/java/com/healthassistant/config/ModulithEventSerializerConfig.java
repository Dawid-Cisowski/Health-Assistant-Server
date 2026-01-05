package com.healthassistant.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthassistant.healthevents.api.dto.StoredEventData;
import com.healthassistant.healthevents.api.dto.events.BaseHealthEventsStoredEvent;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.dto.payload.HealthRating;
import com.healthassistant.healthevents.api.dto.payload.MealType;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.events.core.EventSerializer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Configuration
@Slf4j
class ModulithEventSerializerConfig {

    @Bean
    @Primary
    EventSerializer eventSerializer() {
        ObjectMapper modulithMapper = createSecureObjectMapper();
        return new SecureJacksonEventSerializer(modulithMapper);
    }

    private ObjectMapper createSecureObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        PolymorphicTypeValidator ptv = buildSecureTypeValidator();
        mapper.setPolymorphicTypeValidator(ptv);

        mapper.addMixIn(EventPayload.class, EventPayloadTypingMixIn.class);

        return mapper;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
    private interface EventPayloadTypingMixIn {
    }

    private PolymorphicTypeValidator buildSecureTypeValidator() {
        var builder = BasicPolymorphicTypeValidator.builder();

        collectAllowedDomainTypes().forEach(clazz -> {
            builder.allowIfSubType(clazz);
            builder.allowIfBaseType(clazz);
        });
        collectAllowedJavaTimeTypes().forEach(clazz -> {
            builder.allowIfSubType(clazz);
            builder.allowIfBaseType(clazz);
        });
        collectAllowedCollectionTypes().forEach(clazz -> {
            builder.allowIfSubType(clazz);
            builder.allowIfBaseType(clazz);
        });

        return builder.build();
    }

    private Set<Class<?>> collectAllowedDomainTypes() {
        Set<Class<?>> allowedTypes = new LinkedHashSet<>();

        allowedTypes.add(StoredEventData.class);
        allowedTypes.add(EventId.class);
        allowedTypes.add(DeviceId.class);
        allowedTypes.add(IdempotencyKey.class);

        allowedTypes.add(EventPayload.class);
        collectSealedSubtypes(EventPayload.class, allowedTypes);

        allowedTypes.add(EventType.class);
        collectSealedSubtypes(EventType.class, allowedTypes);

        allowedTypes.add(BaseHealthEventsStoredEvent.class);
        collectSealedSubtypes(BaseHealthEventsStoredEvent.class, allowedTypes);

        allowedTypes.add(MealType.class);
        allowedTypes.add(HealthRating.class);

        log.info("Modulith serializer allowlist contains {} domain types", allowedTypes.size());

        return allowedTypes;
    }

    private void collectSealedSubtypes(Class<?> sealedType, Set<Class<?>> collector) {
        Class<?>[] permitted = sealedType.getPermittedSubclasses();
        if (permitted == null) {
            return;
        }

        Arrays.stream(permitted).forEach(subtype -> {
            collector.add(subtype);
            collectNestedRecordTypes(subtype, collector);
            if (subtype.isSealed()) {
                collectSealedSubtypes(subtype, collector);
            }
        });
    }

    private void collectNestedRecordTypes(Class<?> recordType, Set<Class<?>> collector) {
        if (!recordType.isRecord()) {
            return;
        }

        Stream.of(recordType.getDeclaredClasses())
                .filter(Class::isRecord)
                .forEach(nested -> {
                    collector.add(nested);
                    collectNestedRecordTypes(nested, collector);
                });
    }

    private Set<Class<?>> collectAllowedJavaTimeTypes() {
        return Set.of(
                Instant.class,
                LocalDate.class,
                LocalDateTime.class,
                ZonedDateTime.class
        );
    }

    private Set<Class<?>> collectAllowedCollectionTypes() {
        return Set.of(
                ArrayList.class,
                HashSet.class,
                LinkedHashSet.class,
                List.class,
                Set.class
        );
    }

    @Slf4j
    private static class SecureJacksonEventSerializer implements EventSerializer {

        private final ObjectMapper objectMapper;

        SecureJacksonEventSerializer(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public Object serialize(Object event) {
            try {
                String serialized = objectMapper.writeValueAsString(event);
                log.debug("Serialized event of type {}", event.getClass().getSimpleName());
                return serialized;
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize event of type {}: {}", event.getClass().getName(), e.getMessage());
                throw new IllegalArgumentException("Failed to serialize event: " + event, e);
            }
        }

        @Override
        public <T> T deserialize(Object serialized, Class<T> type) {
            try {
                T deserialized = objectMapper.readValue((String) serialized, type);
                log.debug("Deserialized event to type {}", type.getSimpleName());
                return deserialized;
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize event to {}: {}", type.getName(), e.getMessage());
                throw new IllegalArgumentException("Failed to deserialize event to " + type.getName(), e);
            }
        }
    }
}
