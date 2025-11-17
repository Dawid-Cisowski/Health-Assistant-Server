package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
                                com.healthassistant.domain.event.IdempotencyKey.of(entity.getIdempotencyKey()),
                                com.healthassistant.domain.event.EventType.from(entity.getEventType()),
                                entity.getOccurredAt(),
                                entity.getPayload(),
                                com.healthassistant.domain.event.DeviceId.of(entity.getDeviceId()),
                                com.healthassistant.domain.event.EventId.of(entity.getEventId())
                        ).withCreatedAt(entity.getCreatedAt())
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
                        entity -> entity
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

