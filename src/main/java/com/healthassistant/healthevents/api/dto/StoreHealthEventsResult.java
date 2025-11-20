package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record StoreHealthEventsResult(
        List<EventResult> results,
        Set<LocalDate> affectedDates,
        Set<EventType> eventTypes,
        List<StoredEventData> storedEvents
) {

    public record EventResult(
            int index,
            EventStatus status,
            EventId eventId,
            EventError error
    ) {
    }

    public enum EventStatus {
        stored,
        duplicate,
        invalid
    }

    public record EventError(String field, String message) {
    }
}
