package com.healthassistant.application.summary;

import java.time.Instant;
import java.util.List;
import java.util.Map;

interface HealthEventsQuery {
    List<EventData> findEventsByDateRange(Instant start, Instant end);
    
    record EventData(
        String eventType,
        Instant occurredAt,
        Map<String, Object> payload
    ) {
    }
}

