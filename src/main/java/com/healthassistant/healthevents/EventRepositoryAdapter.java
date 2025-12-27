package com.healthassistant.healthevents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
class EventRepositoryAdapter implements EventRepository {

    private final HealthEventJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void saveAll(List<Event> events) {
        List<HealthEventJpaEntity> entities = events.stream()
                .map(event -> HealthEventJpaEntity.builder()
                        .eventId(event.eventId().value())
                        .idempotencyKey(event.idempotencyKey().value())
                        .eventType(event.eventType().value())
                        .occurredAt(event.occurredAt())
                        .payload(toMap(event.payload()))
                        .deviceId(event.deviceId().value())
                        .createdAt(event.createdAt())
                        .build())
                .toList();
        jpaRepository.saveAll(entities);
        jpaRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Event> findExistingEventsByIdempotencyKeys(List<IdempotencyKey> idempotencyKeys) {
        List<String> keys = idempotencyKeys.stream()
                .map(IdempotencyKey::value)
                .toList();
        return jpaRepository.findByIdempotencyKeyIn(keys).stream()
                .collect(Collectors.toMap(
                        HealthEventJpaEntity::getIdempotencyKey,
                        this::toEvent,
                        (existing, replacement) -> existing.createdAt().isAfter(replacement.createdAt()) ? existing : replacement
                ));
    }

    private Event toEvent(HealthEventJpaEntity entity) {
        EventType eventType = EventType.from(entity.getEventType());
        return Event.create(
                IdempotencyKey.of(entity.getIdempotencyKey()),
                eventType,
                entity.getOccurredAt(),
                toPayload(eventType, entity.getPayload()),
                DeviceId.of(entity.getDeviceId()),
                EventId.of(entity.getEventId())
        ).withCreatedAt(entity.getCreatedAt());
    }

    @Override
    @Transactional
    public void updateAll(List<Event> events) {
        List<String> idempotencyKeys = events.stream()
                .map(e -> e.idempotencyKey().value())
                .toList();

        Map<String, HealthEventJpaEntity> existingEntities = jpaRepository.findByIdempotencyKeyIn(idempotencyKeys)
                .stream()
                .collect(Collectors.toMap(
                        HealthEventJpaEntity::getIdempotencyKey,
                        entity -> entity,
                        (existing, replacement) -> existing.getCreatedAt().isAfter(replacement.getCreatedAt()) ? existing : replacement
                ));

        List<HealthEventJpaEntity> entitiesToUpdate = events.stream()
                .map(event -> {
                    HealthEventJpaEntity existing = existingEntities.get(event.idempotencyKey().value());
                    if (existing == null) {
                        throw new IllegalArgumentException("Event with idempotency key " + event.idempotencyKey().value() + " not found for update");
                    }

                    existing.setEventType(event.eventType().value());
                    existing.setOccurredAt(event.occurredAt());
                    existing.setPayload(toMap(event.payload()));
                    existing.setDeviceId(event.deviceId().value());

                    return existing;
                })
                .toList();

        jpaRepository.saveAll(entitiesToUpdate);
        jpaRepository.flush();
    }

    private Map<String, Object> toMap(EventPayload payload) {
        return objectMapper.convertValue(payload, new TypeReference<>() {});
    }

    private EventPayload toPayload(EventType eventType, Map<String, Object> map) {
        Class<? extends EventPayload> clazz = EventPayload.payloadClassFor(eventType);
        return objectMapper.convertValue(map, clazz);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> findSleepIdempotencyKeyByDeviceIdAndSleepStart(DeviceId deviceId, Instant sleepStart) {
        log.debug("Looking for sleep event with deviceId={}, sleepStart={}", deviceId.value(), sleepStart);

        List<HealthEventJpaEntity> sleepEvents = jpaRepository.findByDeviceIdAndEventType(
                deviceId.value(),
                "SleepSessionRecorded.v1"
        );

        for (HealthEventJpaEntity entity : sleepEvents) {
            Object sleepStartObj = entity.getPayload().get("sleepStart");
            if (sleepStartObj != null) {
                Instant storedSleepStart = parseStoredInstant(sleepStartObj);
                if (storedSleepStart != null && storedSleepStart.equals(sleepStart)) {
                    log.debug("Found matching sleep event with idempotencyKey={}", entity.getIdempotencyKey());
                    return Optional.of(IdempotencyKey.of(entity.getIdempotencyKey()));
                }
            }
        }

        return Optional.empty();
    }

    private Instant parseStoredInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            double epochSeconds = ((Number) value).doubleValue();
            return Instant.ofEpochSecond((long) epochSeconds);
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception e) {
                log.warn("Failed to parse instant from string: {}", value);
                return null;
            }
        }
        return null;
    }
}
