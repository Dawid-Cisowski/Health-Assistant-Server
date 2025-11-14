package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    public Set<String> findExistingIdempotencyKeys(List<IdempotencyKey> idempotencyKeys) {
        List<String> keys = idempotencyKeys.stream()
                .map(IdempotencyKey::value)
                .toList();
        return jpaRepository.findByIdempotencyKeyIn(keys).stream()
                .map(HealthEventJpaEntity::getIdempotencyKey)
                .collect(Collectors.toSet());
    }
}

