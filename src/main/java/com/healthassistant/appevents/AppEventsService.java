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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

        List<IndexedEvent> indexedEvents = IntStream.range(0, request.events().size())
                .mapToObj(i -> new IndexedEvent(i, request.events().get(i)))
                .toList();

        Map<Boolean, List<IndexedEvent>> partitionedEvents = indexedEvents.stream()
                .collect(Collectors.partitioningBy(ie -> ie.event().deserializationError() != null));

        List<EventResult> deserializationErrors = partitionedEvents.get(true).stream()
                .map(ie -> new EventResult(
                        ie.index(),
                        EventStatus.invalid,
                        null,
                        new EventError("payload", ie.event().deserializationError())
                ))
                .toList();

        List<IndexedEvent> validEvents = partitionedEvents.get(false);

        List<EventEnvelope> eventEnvelopes = validEvents.stream()
                .map(ie -> createEventEnvelope(ie, deviceId))
                .toList();

        StoreHealthEventsResult facadeResult = healthEventsFacade.storeHealthEvents(
                new StoreHealthEventsCommand(eventEnvelopes, deviceId)
        );

        List<EventResult> facadeResults = facadeResult.results().stream().map(eventResult -> {
                    int originalIndex = validEvents.get(eventResult.index()).index();
                    return new EventResult(
                            originalIndex,
                            eventResult.status(),
                            eventResult.eventId(),
                            eventResult.error()
                    );
                })
                .toList();

        List<EventResult> allResults = Stream.concat(deserializationErrors.stream(), facadeResults.stream())
                .sorted(Comparator.comparingInt(EventResult::index))
                .toList();

        return new StoreHealthEventsResult(
                allResults,
                facadeResult.affectedDates(),
                facadeResult.eventTypes(),
                facadeResult.storedEvents()
        );
    }

    private EventEnvelope createEventEnvelope(IndexedEvent indexedEvent, DeviceId deviceId) {
        HealthEventRequest event = indexedEvent.event();
        IdempotencyKey idempotencyKey = IdempotencyKey.from(
                event.idempotencyKey(),
                deviceId.value(),
                event.type(),
                event.payload(),
                indexedEvent.index()
        );

        return new EventEnvelope(
                idempotencyKey,
                event.type(),
                event.occurredAt(),
                event.payload()
        );
    }

    private record IndexedEvent(int index, HealthEventRequest event) {}
}
