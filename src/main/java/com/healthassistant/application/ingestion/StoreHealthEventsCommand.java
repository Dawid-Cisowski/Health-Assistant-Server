package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.IdempotencyKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record StoreHealthEventsCommand(
        List<EventEnvelope> events,
        DeviceId deviceId
) {

    public record EventEnvelope(
            IdempotencyKey idempotencyKey,
            String eventType,
            Instant occurredAt,
            Map<String, Object> payload
    ) {
    }
}
