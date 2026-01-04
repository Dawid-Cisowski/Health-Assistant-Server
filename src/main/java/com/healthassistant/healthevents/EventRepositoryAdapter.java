package com.healthassistant.healthevents;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.dto.ExistingSleepInfo;
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
    public void markAsDeleted(String targetEventId, String deletedByEventId, Instant deletedAt) {
        jpaRepository.findByEventId(targetEventId).ifPresent(entity -> {
            entity.setDeletedAt(deletedAt);
            entity.setDeletedByEventId(deletedByEventId);
            jpaRepository.save(entity);
            log.info("Marked event {} as deleted by {}", targetEventId, deletedByEventId);
        });
    }

    @Override
    @Transactional
    public void markAsSuperseded(String targetEventId, String supersededByEventId) {
        jpaRepository.findByEventId(targetEventId).ifPresent(entity -> {
            entity.setSupersededByEventId(supersededByEventId);
            jpaRepository.save(entity);
            log.info("Marked event {} as superseded by {}", targetEventId, supersededByEventId);
        });
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
    public Optional<ExistingSleepInfo> findExistingSleepInfo(DeviceId deviceId, Instant sleepStart) {
        log.debug("Looking for sleep event with deviceId={}, sleepStart={}", deviceId.value(), sleepStart);

        return jpaRepository.findSleepInfoByDeviceIdAndSleepStart(
                        deviceId.value(),
                        sleepStart.toString()
                )
                .map(projection -> new ExistingSleepInfo(
                        IdempotencyKey.of(projection.getIdempotencyKey()),
                        EventId.of(projection.getEventId())
                ));
    }
}
