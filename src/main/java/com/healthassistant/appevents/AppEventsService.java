package com.healthassistant.appevents;

import com.healthassistant.appevents.api.AppEventsFacade;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest.HealthEventRequest;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand.EventEnvelope;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventError;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventResult;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult.EventStatus;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.IntStream;

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

        List<EventResult> deserializationErrorResults = new ArrayList<>();
        List<EventEnvelope> validEventEnvelopes = new ArrayList<>();
        Map<Integer, Integer> validIndexMapping = new HashMap<>();

        IntStream.range(0, request.events().size()).forEach(i -> {
            HealthEventRequest eventRequest = request.events().get(i);
            if (eventRequest.deserializationError() != null) {
                deserializationErrorResults.add(new EventResult(
                        i,
                        EventStatus.invalid,
                        null,
                        new EventError("payload", eventRequest.deserializationError())
                ));
            } else {
                IdempotencyKey idempotencyKey = IdempotencyKey.from(
                        eventRequest.idempotencyKey(),
                        deviceId.value(),
                        eventRequest.type(),
                        eventRequest.payload(),
                        i
                );

                validIndexMapping.put(validEventEnvelopes.size(), i);
                validEventEnvelopes.add(new EventEnvelope(
                        idempotencyKey,
                        eventRequest.type(),
                        eventRequest.occurredAt(),
                        eventRequest.payload()
                ));
            }
        });

        StoreHealthEventsResult facadeResult = healthEventsFacade.storeHealthEvents(
                new StoreHealthEventsCommand(validEventEnvelopes, deviceId)
        );

        List<EventResult> allResults = new ArrayList<>(deserializationErrorResults);

        facadeResult.results().forEach(facadeRes -> {
            int originalIndex = validIndexMapping.get(facadeRes.index());
            allResults.add(new EventResult(
                    originalIndex,
                    facadeRes.status(),
                    facadeRes.eventId(),
                    facadeRes.error()
            ));
        });

        allResults.sort(Comparator.comparingInt(EventResult::index));

        return new StoreHealthEventsResult(
                allResults,
                facadeResult.affectedDates(),
                facadeResult.eventTypes(),
                facadeResult.storedEvents()
        );
    }
}
