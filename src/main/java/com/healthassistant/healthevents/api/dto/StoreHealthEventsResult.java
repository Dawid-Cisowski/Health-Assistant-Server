package com.healthassistant.healthevents.api.dto;

import com.healthassistant.healthevents.api.model.EventId;
import com.healthassistant.healthevents.api.model.EventType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
            CompensationType compensationType,
            String correctedEventType,
            Map<String, Object> correctedPayload,
            Instant correctedOccurredAt
    ) {
        public CompensationTarget(
                String targetEventId,
                String targetEventType,
                Instant targetOccurredAt,
                String deviceId,
                CompensationType compensationType
        ) {
            this(targetEventId, targetEventType, targetOccurredAt, deviceId, compensationType, null, null, null);
        }
    }

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
        STORED("stored"),
        DUPLICATE("duplicate"),
        INVALID("invalid");

        private final String displayName;

        EventStatus(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public record EventError(String field, String message) {
    }
}
