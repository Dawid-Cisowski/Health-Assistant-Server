package com.healthassistant.healthevents.api.dto.events;

import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record AllEventsStoredEvent(
        String deviceId,
        List<StoredEventData> events,
        Set<LocalDate> affectedDates,
        Set<String> eventTypes
) implements BaseHealthEventsStoredEvent {
}
