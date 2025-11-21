package com.healthassistant.appevents;

import com.healthassistant.appevents.api.AppEventsFacade;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest;
import com.healthassistant.appevents.api.dto.SubmitHealthEventsRequest.HealthEventRequest;
import com.healthassistant.healthevents.api.HealthEventsFacade;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand.EventEnvelope;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;
import com.healthassistant.healthevents.api.model.DeviceId;
import com.healthassistant.healthevents.api.model.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
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

        List<EventEnvelope> eventEnvelopes = IntStream.range(0, request.events().size())
                .mapToObj(i -> {
                    HealthEventRequest eventRequest = request.events().get(i);

                    IdempotencyKey idempotencyKey = IdempotencyKey.from(
                            eventRequest.idempotencyKey(),
                            deviceId.value(),
                            eventRequest.type(),
                            eventRequest.payload(),
                            i
                    );

                    return new EventEnvelope(
                            idempotencyKey,
                            eventRequest.type(),
                            eventRequest.occurredAt(),
                            eventRequest.payload()
                    );
                })
                .toList();

        return healthEventsFacade.storeHealthEvents(new StoreHealthEventsCommand(eventEnvelopes, deviceId));
    }
}
