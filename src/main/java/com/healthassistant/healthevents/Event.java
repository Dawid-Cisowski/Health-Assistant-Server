package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

record Event(IdempotencyKey idempotencyKey,
             EventType eventType,
             Instant occurredAt,
             EventPayload payload,
             DeviceId deviceId,
             EventId eventId,
             Instant createdAt,
             String deletedByEventId,
             String supersededByEventId) {

    Event {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey cannot be null");
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        Objects.requireNonNull(deviceId, "deviceId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
    }

    static Event create(IdempotencyKey idempotencyKey, EventType eventType, Instant occurredAt, EventPayload payload, DeviceId deviceId, EventId eventId) {
        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, Instant.now(), null, null);
    }

    boolean isActive() {
        return !isDeleted() && !isSuperseded();
    }

    boolean isDeleted() {
        return deletedByEventId != null;
    }

    boolean isSuperseded() {
        return supersededByEventId != null;
    }

    Optional<LocalDate> affectedDate() {
        return Optional.ofNullable(occurredAt)
                .map(DateTimeUtils::toPolandDate);
    }

    Event withCreatedAt(Instant newCreatedAt) {
        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, newCreatedAt, deletedByEventId, supersededByEventId);
    }

    Event withDeletionInfo(String deletedBy, String supersededBy) {
        return new Event(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId, createdAt, deletedBy, supersededBy);
    }
}
