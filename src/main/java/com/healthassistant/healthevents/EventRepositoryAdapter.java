package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class EventRepositoryAdapter implements EventRepository {

    private final HealthEventJpaRepository jpaRepository;

    @Override
    @Transactional
    public void saveAll(List<Event> events) {
        List<HealthEventJpaEntity> entities = events.stream()
                .map(event -> HealthEventJpaEntity.builder()
                        .eventId(event.eventId().value())
                        .idempotencyKey(event.idempotencyKey().value())
                        .eventType(event.eventType().value())
                        .occurredAt(event.occurredAt())
                        .payload(event.payload())
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
                        entity -> Event.create(
                                IdempotencyKey.of(entity.getIdempotencyKey()),
                                EventType.from(entity.getEventType()),
                                entity.getOccurredAt(),
                                entity.getPayload(),
                                DeviceId.of(entity.getDeviceId()),
                                EventId.of(entity.getEventId())
                        ).withCreatedAt(entity.getCreatedAt()),
                        (existing, replacement) -> existing.createdAt().isAfter(replacement.createdAt()) ? existing : replacement
                ));
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
                    existing.setPayload(event.payload());
                    existing.setDeviceId(event.deviceId().value());

                    return existing;
                })
                .toList();

        jpaRepository.saveAll(entitiesToUpdate);
        jpaRepository.flush();
    }
}
