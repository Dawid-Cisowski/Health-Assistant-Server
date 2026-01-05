package com.healthassistant.healthevents.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;
import com.healthassistant.healthevents.api.model.IdempotencyKey;

import java.io.IOException;
import java.time.Instant;

public class StoredEventDataDeserializer extends StdDeserializer<StoredEventData> {

    private static final long serialVersionUID = 1L;

    public StoredEventDataDeserializer() {
        super(StoredEventData.class);
    }

    @Override
    public StoredEventData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        JsonNode eventTypeNode = node.get("eventType");
        EventType eventType = deserializeEventType(eventTypeNode, mapper);

        Class<? extends EventPayload> payloadClass = EventPayload.payloadClassFor(eventType);

        JsonNode payloadNode = node.get("payload");
        EventPayload payload = payloadNode != null && !payloadNode.isNull()
                ? mapper.treeToValue(payloadNode, payloadClass)
                : null;

        IdempotencyKey idempotencyKey = deserializeIdempotencyKey(node.get("idempotencyKey"), mapper);
        Instant occurredAt = deserializeInstant(node.get("occurredAt"), mapper);
        DeviceId deviceId = deserializeDeviceId(node.get("deviceId"), mapper);
        EventId eventId = deserializeEventId(node.get("eventId"), mapper);

        return new StoredEventData(idempotencyKey, eventType, occurredAt, payload, deviceId, eventId);
    }

    private EventType deserializeEventType(JsonNode node, ObjectMapper mapper) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return EventType.from(node.asText());
        }
        return mapper.treeToValue(node, EventType.class);
    }

    private IdempotencyKey deserializeIdempotencyKey(JsonNode node, ObjectMapper mapper) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return IdempotencyKey.of(node.asText());
        }
        if (node.has("value")) {
            return IdempotencyKey.of(node.get("value").asText());
        }
        return mapper.treeToValue(node, IdempotencyKey.class);
    }

    private Instant deserializeInstant(JsonNode node, ObjectMapper mapper) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.longValue());
        }
        if (node.isTextual()) {
            return Instant.parse(node.asText());
        }
        return mapper.treeToValue(node, Instant.class);
    }

    private DeviceId deserializeDeviceId(JsonNode node, ObjectMapper mapper) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return DeviceId.of(node.asText());
        }
        if (node.has("value")) {
            return DeviceId.of(node.get("value").asText());
        }
        return mapper.treeToValue(node, DeviceId.class);
    }

    private EventId deserializeEventId(JsonNode node, ObjectMapper mapper) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return EventId.of(node.asText());
        }
        if (node.has("value")) {
            return EventId.of(node.get("value").asText());
        }
        return mapper.treeToValue(node, EventId.class);
    }
}
