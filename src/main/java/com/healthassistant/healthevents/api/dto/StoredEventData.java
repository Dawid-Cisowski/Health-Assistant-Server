package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;
import java.util.Map;

public record StoredEventData(
        IdempotencyKey idempotencyKey,
        EventType eventType,
        Instant occurredAt,
        Map<String, Object> payload,
        DeviceId deviceId,
        EventId eventId
) {
}
