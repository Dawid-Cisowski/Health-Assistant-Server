package com.healthassistant.appevents.api.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.JsonNode;
import com.healthassistant.healthevents.api.dto.payload.EventPayload;
import com.healthassistant.healthevents.api.model.EventType;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class HealthEventRequestDeserializer extends ValueDeserializer<SubmitHealthEventsRequest.HealthEventRequest> {

    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("`([^`]+)`");

    @Override
    public SubmitHealthEventsRequest.HealthEventRequest deserialize(JsonParser p, DeserializationContext ctxt) {
        JsonNode node = ctxt.readTree(p);

        String idempotencyKey = extractStringField(node, "idempotencyKey").orElse(null);
        String type = extractStringField(node, "type").orElse(null);
        Instant occurredAt = parseOccurredAt(node);
        String deserializationError = validateRequiredFields(type, occurredAt);

        EventPayload payload = null;
        if (deserializationError == null && hasNonNullField(node, "payload") && type != null) {
            try {
                EventType eventType = EventType.from(type);
                Class<? extends EventPayload> payloadClass = EventPayload.payloadClassFor(eventType);
                payload = ctxt.readTreeAsValue(node.get("payload"), payloadClass);
            } catch (IllegalArgumentException e) {
                deserializationError = "Unknown event type: " + type;
                log.debug("Failed to deserialize event: {}", deserializationError);
            } catch (JacksonException e) {
                deserializationError = extractMeaningfulError(e);
                log.debug("Failed to deserialize payload for type {}: {}", type, deserializationError);
            } catch (RuntimeException e) {
                deserializationError = "Unexpected error processing payload: " + e.getMessage();
                log.error("Unexpected error deserializing payload for type {}: {}", type, e.getMessage(), e);
            }
        }

        return new SubmitHealthEventsRequest.HealthEventRequest(idempotencyKey, type, occurredAt, payload, deserializationError);
    }

    private Instant parseOccurredAt(JsonNode node) {
        return extractStringField(node, "occurredAt")
                .flatMap(this::safeParseInstant)
                .orElse(null);
    }

    private Optional<Instant> safeParseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private String validateRequiredFields(String type, Instant occurredAt) {
        if (type == null) {
            return "Missing required field: type";
        }
        if (occurredAt == null) {
            return "Missing or invalid required field: occurredAt";
        }
        return null;
    }

    private Optional<String> extractStringField(JsonNode node, String fieldName) {
        return Optional.of(node)
                .filter(n -> n.has(fieldName))
                .map(n -> n.get(fieldName))
                .filter(n -> !n.isNull())
                .map(JsonNode::asText);
    }

    private boolean hasNonNullField(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull();
    }

    private String extractMeaningfulError(Exception e) {
        return Optional.ofNullable(e.getMessage())
                .filter(msg -> msg.contains("Cannot deserialize value of type"))
                .flatMap(this::extractTypeNameFromMessage)
                .map(this::toFieldName)
                .map(fieldName -> "Invalid value for " + fieldName)
                .orElseGet(() -> Optional.ofNullable(e.getMessage())
                        .map(msg -> "Invalid payload: " + msg)
                        .orElse("Invalid payload"));
    }

    private Optional<String> extractTypeNameFromMessage(String message) {
        Matcher matcher = TYPE_NAME_PATTERN.matcher(message);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String toFieldName(String fullTypeName) {
        String simpleName = fullTypeName.contains(".")
                ? fullTypeName.substring(fullTypeName.lastIndexOf('.') + 1)
                : fullTypeName;
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }
}
