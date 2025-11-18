package com.healthassistant.application.ingestion;

import com.healthassistant.domain.event.DeviceId;
import com.healthassistant.domain.event.IdempotencyKey;
import com.healthassistant.dto.request.SubmitHealthEventsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HealthEventsFacade {

    private final StoreHealthEventsCommandHandler commandHandler;

    public StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command) {
        return commandHandler.handle(command);
    }

    public StoreHealthEventsResult submitHealthEvents(SubmitHealthEventsRequest request) {
        DeviceId deviceId = new DeviceId(
                request.deviceId() != null ? request.deviceId() : "mobile-app"
        );

        List<StoreHealthEventsCommand.EventEnvelope> eventEnvelopes = new ArrayList<>();

        for (int i = 0; i < request.events().size(); i++) {
            var eventRequest = request.events().get(i);

            String idempotencyKeyValue = eventRequest.idempotencyKey();
            if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
                idempotencyKeyValue = generateIdempotencyKey(
                        deviceId.value(),
                        eventRequest.type(),
                        eventRequest.payload(),
                        i
                );
            }

            IdempotencyKey idempotencyKey = new IdempotencyKey(idempotencyKeyValue);

            StoreHealthEventsCommand.EventEnvelope envelope = new StoreHealthEventsCommand.EventEnvelope(
                    idempotencyKey,
                    eventRequest.type(),
                    eventRequest.occurredAt(),
                    eventRequest.payload()
            );

            eventEnvelopes.add(envelope);
        }

        StoreHealthEventsCommand command = new StoreHealthEventsCommand(eventEnvelopes, deviceId);
        return commandHandler.handle(command);
    }

    private String generateIdempotencyKey(
            String deviceId,
            String eventType,
            Map<String, Object> payload,
            int index) {

        if ("WorkoutRecorded.v1".equals(eventType)) {
            Object workoutId = payload.get("workoutId");
            if (workoutId != null) {
                return deviceId + "|workout|" + workoutId;
            }
        }

        return deviceId + "|" + eventType + "|" + System.currentTimeMillis() + "-" + index;
    }
}
