package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.util.List;

public record StoreHealthEventsCommand(
        List<EventEnvelope> events,
        DeviceId deviceId,
        boolean skipProjections
) {

    public StoreHealthEventsCommand(List<EventEnvelope> events, DeviceId deviceId) {
        this(events, deviceId, false);
    }

    public record EventEnvelope(
            IdempotencyKey idempotencyKey,
            String eventType,
            Instant occurredAt,
            EventPayload payload
    ) {
    }
}
