package com.healthassistant.healthevents.api.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.time.Instant;

public class StoredEventDataDeserializer extends ValueDeserializer<StoredEventData> {

    @Override
    public StoredEventData deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);

        JsonNode eventTypeNode = node.get("eventType");
        EventType eventType = deserializeEventType(eventTypeNode, ctxt);

        Class<? extends EventPayload> payloadClass = EventPayload.payloadClassFor(eventType);

        JsonNode payloadNode = node.get("payload");
        EventPayload payload = payloadNode != null && !payloadNode.isNull()
                ? ctxt.readTreeAsValue(payloadNode, payloadClass)
                : null;

        IdempotencyKey idempotencyKey = deserializeIdempotencyKey(node.get("idempotencyKey"), ctxt);
        Instant occurredAt = deserializeInstant(node.get("occurredAt"), ctxt);
        DeviceId deviceId = deserializeDeviceId(node.get("deviceId"), ctxt);
        EventId eventId = deserializeEventId(node.get("eventId"), ctxt);

        return new StoredEventData(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId);
    }

    private EventType deserializeEventType(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return EventType.from(node.asText());
        }
        return ctxt.readTreeAsValue(node, EventType.class);
    }

    private IdempotencyKey deserializeIdempotencyKey(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return IdempotencyKey.of(node.asText());
        }
        if (node.has("value")) {
            return IdempotencyKey.of(node.get("value").asText());
        }
        return ctxt.readTreeAsValue(node, IdempotencyKey.class);
    }

    private Instant deserializeInstant(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.longValue());
        }
        if (node.isTextual()) {
            return Instant.parse(node.asText());
        }
        return ctxt.readTreeAsValue(node, Instant.class);
    }

    private DeviceId deserializeDeviceId(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return DeviceId.of(node.asText());
        }
        if (node.has("value")) {
            return DeviceId.of(node.get("value").asText());
        }
        return ctxt.readTreeAsValue(node, DeviceId.class);
    }

    private EventId deserializeEventId(JsonNode node, DeserializationContext ctxt) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return EventId.of(node.asText());
        }
        if (node.has("value")) {
            return EventId.of(node.get("value").asText());
        }
        return ctxt.readTreeAsValue(node, EventId.class);
    }
}
