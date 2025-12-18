package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;

record Event(IdempotencyKey idempotencyKey,
             EventType eventType,
             Instant occurredAt,
             EventPayload payload,
             DeviceId deviceId,
             EventId eventId,
             Instant createdAt) {

    static Event create(IdempotencyKey idempotencyKey, EventType eventType, Instant occurredAt, EventPayload payload, DeviceId deviceId, EventId eventId) {
        if (payload == null) {
            throw new IllegalArgumentException("Event payload cannot be null");
        }

        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, Instant.now());
    }

    Event withCreatedAt(Instant createdAt) {
        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, createdAt);
    }
}
