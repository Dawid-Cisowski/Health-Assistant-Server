package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record EventsStoredEvent(
        Set<LocalDate> affectedDates,
        Set<EventType> eventTypes,
        List<StoredEventData> events
) {
}
