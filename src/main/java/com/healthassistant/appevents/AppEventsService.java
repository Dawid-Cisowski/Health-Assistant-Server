package com.healthassistant.appevents;

import com.healthassistant.appevents.api.AppEventsFacade;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
class AppEventsService implements AppEventsFacade {

    private final HealthEventsFacade healthEventsFacade;

    @Override
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
        return healthEventsFacade.storeHealthEvents(command);
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
