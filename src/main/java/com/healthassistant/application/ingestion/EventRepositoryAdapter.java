package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.Event;
import com.healthassistant.domain.event.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class EventRepositoryAdapter implements EventRepository {

    private final HealthEventJpaRepository jpaRepository;

    @Override
    @Transactional
    public void save(Event event) {
        HealthEventJpaEntity entity = HealthEventJpaEntity.builder()
                .eventId(event.eventId().value())
                .idempotencyKey(event.idempotencyKey().value())
                .eventType(event.eventType().value())
                .occurredAt(event.occurredAt())
                .payload(event.payload())
                .deviceId(event.deviceId().value())
                .createdAt(event.createdAt())
                .build();
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(IdempotencyKey idempotencyKey) {
        return jpaRepository.existsByIdempotencyKey(idempotencyKey.value());
    }
}

