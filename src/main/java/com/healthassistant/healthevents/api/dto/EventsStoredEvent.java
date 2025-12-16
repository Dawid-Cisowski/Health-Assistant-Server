package com.healthassistant.healthevents.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record EventsStoredEvent(
        Set<LocalDate> affectedDates,
        Set<String> eventTypes,
        List<StoredEventData> events
) {
}
