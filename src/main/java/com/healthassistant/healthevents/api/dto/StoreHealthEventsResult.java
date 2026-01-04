package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record StoreHealthEventsResult(
        List<EventResult> results,
        Set<LocalDate> affectedDates,
        Set<EventType> eventTypes,
        List<StoredEventData> storedEvents,
        List<CompensationTarget> compensationTargets
) {

    public StoreHealthEventsResult(
            List<EventResult> results,
            Set<LocalDate> affectedDates,
            Set<EventType> eventTypes,
            List<StoredEventData> storedEvents
    ) {
        this(results, affectedDates, eventTypes, storedEvents, List.of());
    }

    public record CompensationTarget(
            String targetEventId,
            String targetEventType,
            Instant targetOccurredAt,
            String deviceId,
            CompensationType compensationType
    ) {}

    public enum CompensationType {
        DELETED,
        CORRECTED
    }

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
