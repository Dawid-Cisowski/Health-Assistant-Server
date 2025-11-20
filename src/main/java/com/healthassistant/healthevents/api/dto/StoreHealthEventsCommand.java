package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

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
