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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class HealthEventRequestDeserializer extends JsonDeserializer<SubmitHealthEventsRequest.HealthEventRequest> {

    private static final Pattern TYPE_NAME_PATTERN = Pattern.compile("`([^`]+)`");

    @Override
    public SubmitHealthEventsRequest.HealthEventRequest deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        String idempotencyKey = extractStringField(node, "idempotencyKey").orElse(null);
        String type = extractStringField(node, "type").orElse(null);
        Instant occurredAt = extractStringField(node, "occurredAt")
                .map(Instant::parse)
                .orElse(null);

        EventPayload payload = null;
        String deserializationError = null;
        if (hasNonNullField(node, "payload") && type != null) {
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
