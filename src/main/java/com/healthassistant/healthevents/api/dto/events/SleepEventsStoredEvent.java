package com.healthassistant.healthevents.api.dto.events;

import com.healthassistant.healthevents.api.dto.StoredEventData;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record SleepEventsStoredEvent(
        List<StoredEventData> events,
        Set<LocalDate> affectedDates
) implements BaseHealthEventsStoredEvent {
}
