package com.healthassistant.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthassistant.dto.payload.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventEnvelopeDeserializer extends JsonDeserializer<EventEnvelope> {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Override
    public EventEnvelope deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        
        String idempotencyKey = node.has("idempotencyKey") && !node.get("idempotencyKey").isNull() ? node.get("idempotencyKey").asText() : null;
        String type = node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null;
        String occurredAt = node.has("occurredAt") && !node.get("occurredAt").isNull() ? node.get("occurredAt").asText() : null;
        JsonNode payloadNode = node.get("payload");
        
        if (payloadNode != null && payloadNode.isNull()) {
            throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
                p,
                "payload is required",
                payloadNode,
                EventPayload.class
            );
        }
        
        EventPayload payload = null;
        if (payloadNode != null && !payloadNode.isNull() && type != null) {
            if (payloadNode.isObject() && payloadNode.size() == 0) {
                payload = null;
            } else if (!isKnownEventType(type)) {
                payload = null;
            } else {
                try {
                    payload = deserializePayload(type, payloadNode, p, ctxt);
                } catch (Exception e) {
                    payload = null;
                }
            }
        }
        
        java.time.Instant occurredAtInstant = null;
        if (occurredAt != null) {
            try {
                occurredAtInstant = java.time.Instant.parse(occurredAt);
            } catch (Exception e) {
                throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
                    p,
                    "Invalid timestamp format: " + occurredAt,
                    node.get("occurredAt"),
                    java.time.Instant.class
                );
            }
        }
        
        return EventEnvelope.builder()
            .idempotencyKey(idempotencyKey)
            .type(type)
            .occurredAt(occurredAtInstant)
            .payload(payload)
            .build();
    }
    
    private boolean isKnownEventType(String eventType) {
        if (eventType == null) {
            return false;
        }
        return switch (eventType) {
            case "StepsBucketedRecorded.v1",
                 "HeartRateSummaryRecorded.v1",
                 "SleepSessionRecorded.v1",
                 "ActiveCaloriesBurnedRecorded.v1",
                 "ActiveMinutesRecorded.v1",
                 "ExerciseSessionRecorded.v1" -> true;
            default -> false;
        };
    }
    
    private EventPayload deserializePayload(String eventType, JsonNode payloadNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        if (payloadNode == null || payloadNode.isNull()) {
            return null;
        }
        
        try {
            return switch (eventType) {
                case "StepsBucketedRecorded.v1" -> deserializeStepsPayload(payloadNode);
                case "HeartRateSummaryRecorded.v1" -> deserializeHeartRatePayload(payloadNode);
                case "SleepSessionRecorded.v1" -> deserializeSleepSessionPayload(payloadNode);
                case "ActiveCaloriesBurnedRecorded.v1" -> deserializeActiveCaloriesPayload(payloadNode);
                case "ActiveMinutesRecorded.v1" -> deserializeActiveMinutesPayload(payloadNode);
                case "ExerciseSessionRecorded.v1" -> deserializeExerciseSessionPayload(payloadNode, p);
                default -> {
                    throw new IllegalArgumentException("Unknown event type: " + eventType);
                }
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }
    
    private StepsPayload deserializeStepsPayload(JsonNode node) throws IOException {
        return new StepsPayload(
            parseInstantField(node, "bucketStart"),
            parseInstantField(node, "bucketEnd"),
            getIntField(node, "count"),
            getTextField(node, "originPackage")
        );
    }

    private HeartRatePayload deserializeHeartRatePayload(JsonNode node) throws IOException {
        return new HeartRatePayload(
            parseInstantField(node, "bucketStart"),
            parseInstantField(node, "bucketEnd"),
            getDoubleField(node, "avg"),
            getIntField(node, "min"),
            getIntField(node, "max"),
            getIntField(node, "samples"),
            getTextField(node, "originPackage")
        );
    }

    private SleepSessionPayload deserializeSleepSessionPayload(JsonNode node) throws IOException {
        return new SleepSessionPayload(
            parseInstantField(node, "sleepStart"),
            parseInstantField(node, "sleepEnd"),
            getIntField(node, "totalMinutes"),
            getTextField(node, "originPackage")
        );
    }

    private ActiveCaloriesPayload deserializeActiveCaloriesPayload(JsonNode node) throws IOException {
        return new ActiveCaloriesPayload(
            parseInstantField(node, "bucketStart"),
            parseInstantField(node, "bucketEnd"),
            getDoubleField(node, "energyKcal"),
            getTextField(node, "originPackage")
        );
    }

    private ActiveMinutesPayload deserializeActiveMinutesPayload(JsonNode node) throws IOException {
        return new ActiveMinutesPayload(
            parseInstantField(node, "bucketStart"),
            parseInstantField(node, "bucketEnd"),
            getIntField(node, "activeMinutes"),
            getTextField(node, "originPackage")
        );
    }

    private ExerciseSessionPayload deserializeExerciseSessionPayload(JsonNode node, JsonParser p) throws IOException {
        return new ExerciseSessionPayload(
            getTextField(node, "sessionId"),
            getTextField(node, "type"),
            parseInstantField(node, "start"),
            parseInstantField(node, "end"),
            getIntField(node, "durationMinutes"),
            getTextField(node, "distanceMeters"),
            getIntField(node, "steps"),
            getTextField(node, "avgSpeedMetersPerSecond"),
            getIntField(node, "avgHr"),
            getIntField(node, "maxHr"),
            getTextField(node, "originPackage")
        );
    }

    private java.time.Instant parseInstantField(JsonNode parent, String fieldName) throws IOException {
        JsonNode node = parent.get(fieldName);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return parseInstant(node);
    }

    private String getTextField(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
            ? node.get(fieldName).asText()
            : null;
    }

    private Integer getIntField(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
            ? node.get(fieldName).asInt()
            : null;
    }

    private Double getDoubleField(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull()
            ? node.get(fieldName).asDouble()
            : null;
    }
    
    private java.time.Instant parseInstant(JsonNode node) throws IOException {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return java.time.Instant.parse(node.asText());
        }
        throw new IOException("Cannot parse Instant from: " + node);
    }
}

