package com.healthassistant.healthevents;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
        )
                .withCreatedAt(entity.getCreatedAt())
                .withDeletionInfo(entity.getDeletedByEventId(), entity.getSupersededByEventId());
    }

    @Override
    @Transactional
    public Optional<CompensationTargetInfo> markAsDeleted(String targetEventId, String deletedByEventId, Instant deletedAt, String requestingDeviceId) {
        return jpaRepository.findByEventIdAndDeviceId(targetEventId, requestingDeviceId).map(entity -> {
            entity.markAsDeleted(deletedAt, deletedByEventId);
            jpaRepository.save(entity);
            log.warn("AUDIT: Marked event {} as deleted by {} for device {}", targetEventId, deletedByEventId, requestingDeviceId);
            return new CompensationTargetInfo(
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getOccurredAt(),
                    entity.getDeviceId()
            );
        });
    }

    @Override
    @Transactional
    public Optional<CompensationTargetInfo> markAsSuperseded(String targetEventId, String supersededByEventId, String requestingDeviceId) {
        return jpaRepository.findByEventIdAndDeviceId(targetEventId, requestingDeviceId).map(entity -> {
            entity.markAsSuperseded(supersededByEventId);
            jpaRepository.save(entity);
            log.warn("AUDIT: Marked event {} as superseded by {} for device {}", targetEventId, supersededByEventId, requestingDeviceId);
            return new CompensationTargetInfo(
                    entity.getEventId(),
                    entity.getEventType(),
                    entity.getOccurredAt(),
                    entity.getDeviceId()
            );
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findByEventIdAndDeviceId(String eventId, String deviceId) {
        return jpaRepository.findByEventIdAndDeviceId(eventId, deviceId)
                .map(this::toEvent);
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

        return jpaRepository.findSleepInfoByDeviceIdAndSleepStart(deviceId.value(), sleepStart)
                .map(this::toExistingSleepInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ExistingSleepInfo> findOverlappingSleepInfo(DeviceId deviceId, Instant sleepStart, Instant sleepEnd) {
        log.debug("Looking for overlapping sleep event with deviceId={}, sleepStart={}, sleepEnd={}",
                deviceId.value(), sleepStart, sleepEnd);

        return jpaRepository.findOverlappingSleepInfo(deviceId.value(), sleepStart, sleepEnd)
                .map(this::toExistingSleepInfo);
    }

    private ExistingSleepInfo toExistingSleepInfo(SleepInfoProjection projection) {
        return new ExistingSleepInfo(
                IdempotencyKey.of(projection.getIdempotencyKey()),
                EventId.of(projection.getEventId()),
                projection.getSleepStart(),
                projection.getSleepEnd()
        );
    }
}
