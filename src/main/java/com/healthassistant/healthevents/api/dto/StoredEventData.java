package com.healthassistant.healthevents.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;

@JsonDeserialize(using = StoredEventDataDeserializer.class)
public record StoredEventData(
        IdempotencyKey idempotencyKey,
        EventType eventType,
        Instant occurredAt,
        EventPayload payload,
        DeviceId deviceId,
        EventId eventId
) {
}
