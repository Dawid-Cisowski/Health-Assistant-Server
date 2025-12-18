package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.dto.payload.EventPayload;

import java.time.Instant;

public record EventData(
        String eventType,
        Instant occurredAt,
        EventPayload payload,
        String deviceId,
        String idempotencyKey
) {
}
