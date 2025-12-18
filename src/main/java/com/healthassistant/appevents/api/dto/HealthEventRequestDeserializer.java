package com.healthassistant.appevents.api.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.EventType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;

@Slf4j
public class HealthEventRequestDeserializer extends JsonDeserializer<SubmitHealthEventsRequest.HealthEventRequest> {

    @Override
    public SubmitHealthEventsRequest.HealthEventRequest deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        String idempotencyKey = node.has("idempotencyKey") && !node.get("idempotencyKey").isNull()
                ? node.get("idempotencyKey").asText()
                : null;

        String type = node.has("type") && !node.get("type").isNull()
                ? node.get("type").asText()
                : null;

        Instant occurredAt = node.has("occurredAt") && !node.get("occurredAt").isNull()
                ? Instant.parse(node.get("occurredAt").asText())
                : null;

        EventPayload payload = null;
        String deserializationError = null;
        if (node.has("payload") && !node.get("payload").isNull() && type != null) {
            try {
                EventType eventType = EventType.from(type);
                Class<? extends EventPayload> payloadClass = EventPayload.payloadClassFor(eventType);
                payload = mapper.treeToValue(node.get("payload"), payloadClass);
            } catch (IllegalArgumentException e) {
                deserializationError = "Unknown event type: " + type;
                log.debug("Failed to deserialize event: {}", deserializationError);
            } catch (Exception e) {
                deserializationError = extractMeaningfulError(e);
                log.debug("Failed to deserialize payload for type {}: {}", type, deserializationError);
            }
        }

        return new SubmitHealthEventsRequest.HealthEventRequest(idempotencyKey, type, occurredAt, payload, deserializationError);
    }

    private String extractMeaningfulError(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "Invalid payload";
        }
        // Extract field name from Jackson error messages like:
        // "Cannot deserialize value of type `com.healthassistant...MealType` from String \"SUPPER\": not one of the values accepted..."
        if (message.contains("Cannot deserialize value of type")) {
            int fieldStart = message.indexOf("`");
            int fieldEnd = message.indexOf("`", fieldStart + 1);
            if (fieldStart >= 0 && fieldEnd > fieldStart) {
                String fullTypeName = message.substring(fieldStart + 1, fieldEnd);
                // Get simple class name (e.g., com.foo.MealType -> MealType)
                String simpleName = fullTypeName.contains(".")
                        ? fullTypeName.substring(fullTypeName.lastIndexOf('.') + 1)
                        : fullTypeName;
                // Convert type name to field name (e.g., MealType -> mealType)
                String fieldName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
                return "Invalid value for " + fieldName;
            }
        }
        return "Invalid payload: " + message;
    }
}
