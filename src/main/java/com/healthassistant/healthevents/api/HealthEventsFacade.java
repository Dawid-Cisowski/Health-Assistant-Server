package com.healthassistant.healthevents.api;

import com.healthassistant.healthevents.api.dto.EventData;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsCommand;
import com.healthassistant.healthevents.api.dto.StoreHealthEventsResult;

import java.time.Instant;
import java.util.List;

public interface HealthEventsFacade {
    StoreHealthEventsResult storeHealthEvents(StoreHealthEventsCommand command);

    List<EventData> findEventsByOccurredAtBetween(Instant start, Instant end);

    void deleteAllEvents();
}
