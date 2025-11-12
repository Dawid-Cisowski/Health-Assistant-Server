package com.healthassistant.infrastructure.web.rest.mapper;

import com.healthassistant.application.ingestion.StoreHealthEventsCommand;
import com.healthassistant.application.ingestion.StoreHealthEventsResult;
import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.dto.EventEnvelope;
import com.healthassistant.dto.HealthEventsRequest;
import com.healthassistant.dto.HealthEventsResponse;

import java.util.List;

public class HealthEventsMapper {
    
    public static StoreHealthEventsCommand toCommand(HealthEventsRequest request, String deviceIdStr) {
        DeviceId deviceId = DeviceId.of(deviceIdStr);
        
        List<StoreHealthEventsCommand.EventEnvelope> commandEnvelopes = request.getEvents().stream()
            .map(HealthEventsMapper::toCommandEnvelope)
            .toList();
        
        return new StoreHealthEventsCommand(commandEnvelopes, deviceId);
    }
    
    private static StoreHealthEventsCommand.EventEnvelope toCommandEnvelope(EventEnvelope dto) {
        return new StoreHealthEventsCommand.EventEnvelope(
            IdempotencyKey.of(dto.getIdempotencyKey()),
            dto.getType(),
            dto.getOccurredAt(),
            dto.getPayload()
        );
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
