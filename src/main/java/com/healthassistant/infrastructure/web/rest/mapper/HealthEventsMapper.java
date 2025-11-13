package com.healthassistant.infrastructure.web.rest.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.application.ingestion.StoreHealthEventsResult;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.dto.EventEnvelope;
import com.healthassistant.dto.HealthEventsRequest;
import com.healthassistant.dto.HealthEventsResponse;
import com.healthassistant.dto.payload.*;

import java.util.List;
import java.util.Map;

public class HealthEventsMapper {
    
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    
    public static StoreHealthEventsCommand toCommand(HealthEventsRequest request, String deviceIdStr) {
        DeviceId deviceId = DeviceId.of(deviceIdStr);
        
        List<StoreHealthEventsCommand.EventEnvelope> commandEnvelopes = request.getEvents().stream()
            .map(HealthEventsMapper::toCommandEnvelope)
            .toList();
        
        return new StoreHealthEventsCommand(commandEnvelopes, deviceId);
    }
    
    private static StoreHealthEventsCommand.EventEnvelope toCommandEnvelope(EventEnvelope dto) {
        Map<String, Object> payloadMap = dto.getPayload() != null 
            ? convertPayloadToMap(dto.getPayload())
            : java.util.Map.of();
        
        String idempotencyKeyStr = dto.getIdempotencyKey();
        IdempotencyKey idempotencyKey;
        try {
            idempotencyKey = idempotencyKeyStr != null && !idempotencyKeyStr.isBlank() 
                ? IdempotencyKey.of(idempotencyKeyStr)
                : IdempotencyKey.of("temp-key-for-validation");
        } catch (Exception e) {
            idempotencyKey = IdempotencyKey.of("temp-key-for-validation");
        }
        
        return new StoreHealthEventsCommand.EventEnvelope(
            idempotencyKey,
            dto.getType(),
            dto.getOccurredAt(),
            payloadMap
        );
    }
    
    private static Map<String, Object> convertPayloadToMap(EventPayload payload) {
        if (payload == null) {
            return java.util.Map.of();
        }
        Map<String, Object> map = objectMapper.convertValue(payload, Map.class);
        
        return switch (payload) {
            case StepsPayload p -> convertInstantFields(map, "bucketStart", "bucketEnd");
            case HeartRatePayload p -> convertInstantFields(map, "bucketStart", "bucketEnd");
            case SleepSessionPayload p -> convertInstantFields(map, "sleepStart", "sleepEnd");
            case ActiveCaloriesPayload p -> convertInstantFields(map, "bucketStart", "bucketEnd");
            case ActiveMinutesPayload p -> convertInstantFields(map, "bucketStart", "bucketEnd");
            case ExerciseSessionPayload p -> {
                convertInstantFields(map, "start", "end");
                normalizeExerciseType(map);
                yield map;
            }
        };
    }
    
    private static void normalizeExerciseType(Map<String, Object> map) {
        Object type = map.get("type");
        if (type != null && "other_0".equals(type.toString())) {
            map.put("type", "WALK");
        }
    }
    
    private static Map<String, Object> convertInstantFields(Map<String, Object> map, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Object value = map.get(fieldName);
            if (value instanceof java.time.Instant instant) {
                map.put(fieldName, instant.toString());
            } else if (value == null) {
                map.put(fieldName, null);
            }
        }
        return map;
    }
    
    public static HealthEventsResponse toResponse(StoreHealthEventsResult result) {
        List<HealthEventsResponse.EventResult> responseResults = result.results().stream()
            .map(HealthEventsMapper::toResponseResult)
            .toList();
        
        return HealthEventsResponse.builder()
            .results(responseResults)
            .build();
    }
    
    private static HealthEventsResponse.EventResult toResponseResult(StoreHealthEventsResult.EventResult commandResult) {
        HealthEventsResponse.EventStatus status = switch (commandResult.status()) {
            case stored -> HealthEventsResponse.EventStatus.stored;
            case duplicate -> HealthEventsResponse.EventStatus.duplicate;
            case invalid -> HealthEventsResponse.EventStatus.invalid;
        };
        
        String eventIdStr = commandResult.eventId() != null 
            ? commandResult.eventId().value() 
            : null;
        
        HealthEventsResponse.ItemError error = commandResult.error() != null
            ? HealthEventsResponse.ItemError.builder()
                .field(commandResult.error().field())
                .message(commandResult.error().message())
                .build()
            : null;
        
        return HealthEventsResponse.EventResult.builder()
            .index(commandResult.index())
            .status(status)
            .eventId(eventIdStr)
            .error(error)
            .build();
    }
}
