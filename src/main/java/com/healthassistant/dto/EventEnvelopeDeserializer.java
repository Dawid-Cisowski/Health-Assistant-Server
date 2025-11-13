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
        JsonNode bucketStartNode = node.get("bucketStart");
        JsonNode bucketEndNode = node.get("bucketEnd");
        return new StepsPayload(
            (bucketStartNode != null && !bucketStartNode.isNull() && !bucketStartNode.isMissingNode()) ? parseInstant(bucketStartNode) : null,
            (bucketEndNode != null && !bucketEndNode.isNull() && !bucketEndNode.isMissingNode()) ? parseInstant(bucketEndNode) : null,
            node.has("count") && !node.get("count").isNull() ? node.get("count").asInt() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
        );
    }
    
    private HeartRatePayload deserializeHeartRatePayload(JsonNode node) throws IOException {
        JsonNode bucketStartNode = node.get("bucketStart");
        JsonNode bucketEndNode = node.get("bucketEnd");
        return new HeartRatePayload(
            (bucketStartNode != null && !bucketStartNode.isNull() && !bucketStartNode.isMissingNode()) ? parseInstant(bucketStartNode) : null,
            (bucketEndNode != null && !bucketEndNode.isNull() && !bucketEndNode.isMissingNode()) ? parseInstant(bucketEndNode) : null,
            node.has("avg") && !node.get("avg").isNull() ? node.get("avg").asDouble() : null,
            node.has("min") && !node.get("min").isNull() ? node.get("min").asInt() : null,
            node.has("max") && !node.get("max").isNull() ? node.get("max").asInt() : null,
            node.has("samples") && !node.get("samples").isNull() ? node.get("samples").asInt() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
        );
    }
    
    private SleepSessionPayload deserializeSleepSessionPayload(JsonNode node) throws IOException {
        JsonNode sleepStartNode = node.get("sleepStart");
        JsonNode sleepEndNode = node.get("sleepEnd");
        return new SleepSessionPayload(
            (sleepStartNode != null && !sleepStartNode.isNull() && !sleepStartNode.isMissingNode()) ? parseInstant(sleepStartNode) : null,
            (sleepEndNode != null && !sleepEndNode.isNull() && !sleepEndNode.isMissingNode()) ? parseInstant(sleepEndNode) : null,
            node.has("totalMinutes") && !node.get("totalMinutes").isNull() ? node.get("totalMinutes").asInt() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
        );
    }
    
    private ActiveCaloriesPayload deserializeActiveCaloriesPayload(JsonNode node) throws IOException {
        JsonNode bucketStartNode = node.get("bucketStart");
        JsonNode bucketEndNode = node.get("bucketEnd");
        return new ActiveCaloriesPayload(
            (bucketStartNode != null && !bucketStartNode.isNull() && !bucketStartNode.isMissingNode()) ? parseInstant(bucketStartNode) : null,
            (bucketEndNode != null && !bucketEndNode.isNull() && !bucketEndNode.isMissingNode()) ? parseInstant(bucketEndNode) : null,
            node.has("energyKcal") && !node.get("energyKcal").isNull() ? node.get("energyKcal").asDouble() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
        );
    }
    
    private ActiveMinutesPayload deserializeActiveMinutesPayload(JsonNode node) throws IOException {
        JsonNode bucketStartNode = node.get("bucketStart");
        JsonNode bucketEndNode = node.get("bucketEnd");
        return new ActiveMinutesPayload(
            (bucketStartNode != null && !bucketStartNode.isNull() && !bucketStartNode.isMissingNode()) ? parseInstant(bucketStartNode) : null,
            (bucketEndNode != null && !bucketEndNode.isNull() && !bucketEndNode.isMissingNode()) ? parseInstant(bucketEndNode) : null,
            node.has("activeMinutes") && !node.get("activeMinutes").isNull() ? node.get("activeMinutes").asInt() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
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
        
        JsonNode startNode = node.get("start");
        JsonNode endNode = node.get("end");
        
        return new ExerciseSessionPayload(
            node.has("sessionId") && !node.get("sessionId").isNull() ? node.get("sessionId").asText() : null,
            node.has("type") && !node.get("type").isNull() ? node.get("type").asText() : null,
            (startNode != null && !startNode.isNull() && !startNode.isMissingNode()) ? parseInstant(startNode) : null,
            (endNode != null && !endNode.isNull() && !endNode.isMissingNode()) ? parseInstant(endNode) : null,
            node.has("durationMinutes") && !node.get("durationMinutes").isNull() ? node.get("durationMinutes").asInt() : null,
            node.has("distanceMeters") && !node.get("distanceMeters").isNull() ? node.get("distanceMeters").asText() : null,
            node.has("steps") && !node.get("steps").isNull() ? node.get("steps").asInt() : null,
            node.has("avgSpeedMetersPerSecond") && !node.get("avgSpeedMetersPerSecond").isNull() ? node.get("avgSpeedMetersPerSecond").asText() : null,
            node.has("avgHr") && !node.get("avgHr").isNull() ? node.get("avgHr").asInt() : null,
            node.has("maxHr") && !node.get("maxHr").isNull() ? node.get("maxHr").asInt() : null,
            node.has("originPackage") && !node.get("originPackage").isNull() ? node.get("originPackage").asText() : null
        );
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

