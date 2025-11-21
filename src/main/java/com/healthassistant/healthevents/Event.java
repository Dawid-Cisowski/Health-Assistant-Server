package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

record Event(IdempotencyKey idempotencyKey,
             EventType eventType,
             Instant occurredAt,
             Map<String, Object> payload,
             DeviceId deviceId,
             EventId eventId,
             Instant createdAt) {

    static Event create(IdempotencyKey idempotencyKey, EventType eventType, Instant occurredAt, Map<String, Object> payload, DeviceId deviceId, EventId eventId) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("Event payload cannot be null or empty");
        }

        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, Instant.now());
    }

    @Override
    public Map<String, Object> payload() {
        if (payload == null) {
            return Map.of();
        }
        return new HashMap<>(payload);
    }

    Event withCreatedAt(Instant createdAt) {
        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, createdAt);
    }
}
