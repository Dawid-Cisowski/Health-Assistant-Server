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
        
        String idempotencyKey = node.get("idempotencyKey").asText();
        String type = node.get("type").asText();
        String occurredAt = node.get("occurredAt").asText();
        JsonNode payloadNode = node.get("payload");
        
        EventPayload payload = deserializePayload(type, payloadNode, p, ctxt);
        
        return EventEnvelope.builder()
            .idempotencyKey(idempotencyKey)
            .type(type)
            .occurredAt(java.time.Instant.parse(occurredAt))
            .payload(payload)
            .build();
    }
    
    private EventPayload deserializePayload(String eventType, JsonNode payloadNode, JsonParser p, DeserializationContext ctxt) throws IOException {
        try {
            return switch (eventType) {
                case "StepsBucketedRecorded.v1" -> deserializeStepsPayload(payloadNode);
                case "HeartRateSummaryRecorded.v1" -> deserializeHeartRatePayload(payloadNode);
                case "SleepSessionRecorded.v1" -> deserializeSleepSessionPayload(payloadNode);
                case "ActiveCaloriesBurnedRecorded.v1" -> deserializeActiveCaloriesPayload(payloadNode);
                case "ActiveMinutesRecorded.v1" -> deserializeActiveMinutesPayload(payloadNode);
                case "ExerciseSessionRecorded.v1" -> deserializeExerciseSessionPayload(payloadNode, p);
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        } catch (Exception e) {
            throw new com.fasterxml.jackson.databind.exc.InvalidFormatException(
                p, 
                "Failed to deserialize payload for event type: " + eventType + ": " + e.getMessage(),
                payloadNode,
                EventPayload.class
            );
        }
    }
    
    private StepsPayload deserializeStepsPayload(JsonNode node) throws IOException {
        return new StepsPayload(
            parseInstant(node.get("bucketStart")),
            parseInstant(node.get("bucketEnd")),
            node.has("count") ? node.get("count").asInt() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private HeartRatePayload deserializeHeartRatePayload(JsonNode node) throws IOException {
        return new HeartRatePayload(
            parseInstant(node.get("bucketStart")),
            parseInstant(node.get("bucketEnd")),
            node.has("avg") ? node.get("avg").asDouble() : null,
            node.has("min") ? node.get("min").asInt() : null,
            node.has("max") ? node.get("max").asInt() : null,
            node.has("samples") ? node.get("samples").asInt() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private SleepSessionPayload deserializeSleepSessionPayload(JsonNode node) throws IOException {
        return new SleepSessionPayload(
            parseInstant(node.get("sleepStart")),
            parseInstant(node.get("sleepEnd")),
            node.has("totalMinutes") ? node.get("totalMinutes").asInt() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private ActiveCaloriesPayload deserializeActiveCaloriesPayload(JsonNode node) throws IOException {
        return new ActiveCaloriesPayload(
            parseInstant(node.get("bucketStart")),
            parseInstant(node.get("bucketEnd")),
            node.has("energyKcal") ? node.get("energyKcal").asDouble() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private ActiveMinutesPayload deserializeActiveMinutesPayload(JsonNode node) throws IOException {
        return new ActiveMinutesPayload(
            parseInstant(node.get("bucketStart")),
            parseInstant(node.get("bucketEnd")),
            node.has("activeMinutes") ? node.get("activeMinutes").asInt() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private ExerciseSessionPayload deserializeExerciseSessionPayload(JsonNode node, JsonParser p) throws IOException {
        String payloadJson = objectMapper.writeValueAsString(node);
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        
        System.out.println("=== ExerciseSessionRecorded.v1 PAYLOAD DEBUG ===");
        System.out.println("Full payload JSON: " + payloadJson);
        System.out.println("Payload fields: " + String.join(", ", fieldNames));
        System.out.println("================================================");
        
        return new ExerciseSessionPayload(
            node.has("sessionId") ? node.get("sessionId").asText() : null,
            node.has("type") ? node.get("type").asText() : null,
            parseInstant(node.get("start")),
            parseInstant(node.get("end")),
            node.has("durationMinutes") ? node.get("durationMinutes").asInt() : null,
            node.has("distanceMeters") ? node.get("distanceMeters").asText() : null,
            node.has("steps") ? node.get("steps").asInt() : null,
            node.has("avgSpeedMetersPerSecond") ? node.get("avgSpeedMetersPerSecond").asText() : null,
            node.has("avgHr") && !node.get("avgHr").isNull() ? node.get("avgHr").asInt() : null,
            node.has("maxHr") && !node.get("maxHr").isNull() ? node.get("maxHr").asInt() : null,
            node.has("originPackage") ? node.get("originPackage").asText() : null
        );
    }
    
    private java.time.Instant parseInstant(JsonNode node) throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return java.time.Instant.parse(node.asText());
        }
        throw new IOException("Cannot parse Instant from: " + node);
    }
}

