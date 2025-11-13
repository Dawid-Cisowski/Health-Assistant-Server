package com.healthassistant.application.summary;


import com.healthassistant.application.ingestion.HealthEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
class HealthEventsQueryAdapter implements HealthEventsQuery {

    private final HealthEventJpaRepository jpaRepository;

    @Override
    public List<EventData> findEventsByDateRange(Instant start, Instant end) {
        return jpaRepository.findByOccurredAtBetween(start, end).stream()
                .map(entity -> new EventData(
                        entity.getEventType(),
                        entity.getOccurredAt(),
                        entity.getPayload()
                ))
                .toList();
    }
}

