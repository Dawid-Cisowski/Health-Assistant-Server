package com.healthassistant.domain.event;

import java.time.Instant;
import java.util.Map;

public record Event(IdempotencyKey idempotencyKey,
                    EventType eventType,
                    Instant occurredAt,
                    Map<String, Object> payload,
                    DeviceId deviceId,
                    EventId eventId,
                    Instant createdAt) {

    public static Event create(IdempotencyKey idempotencyKey, EventType eventType, Instant occurredAt, Map<String, Object> payload, DeviceId deviceId, EventId eventId) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Event payload cannot be null or empty");
        }

        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, Instant.now());
    }

    @Override
    public Map<String, Object> payload() {
        return Map.copyOf(payload);
    }
}

