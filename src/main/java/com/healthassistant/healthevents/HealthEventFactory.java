package com.healthassistant.healthevents;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
class HealthEventFactory {

    private final EventIdGenerator eventIdGenerator;

    Event createNew(
            IdempotencyKey idempotencyKey,
            EventType eventType,
            Instant occurredAt,
            EventPayload payload,
            DeviceId deviceId
    ) {
        return Event.create(idempotencyKey, eventType, occurredAt, payload, deviceId, eventIdGenerator.generate());
    }

    Event createUpdate(
            Event existingEvent,
            EventType eventType,
            Instant occurredAt,
            EventPayload payload,
            DeviceId deviceId
    ) {
        return Event.create(
                existingEvent.idempotencyKey(),
                eventType,
                occurredAt,
                payload,
                deviceId,
                existingEvent.eventId()
        ).withCreatedAt(existingEvent.createdAt())
         .withDeletionInfo(existingEvent.deletedByEventId(), existingEvent.supersededByEventId());
    }
}
