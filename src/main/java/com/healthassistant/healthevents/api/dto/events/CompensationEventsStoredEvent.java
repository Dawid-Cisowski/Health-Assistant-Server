package com.healthassistant.healthevents.api.dto.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record CompensationEventsStoredEvent(
        String deviceId,
        List<CompensationEventData> deletions,
        List<CorrectionEventData> corrections
) {

    public record CompensationEventData(
            String compensationEventId,
            String targetEventId,
            String targetEventType,
            Set<LocalDate> affectedDates
    ) {}

    public record CorrectionEventData(
            String compensationEventId,
            String targetEventId,
            String targetEventType,
            Set<LocalDate> affectedDates,
            String correctedEventType,
            Map<String, Object> correctedPayload,
            Instant correctedOccurredAt
    ) {}

    public boolean hasAnyCompensations() {
        return !deletions.isEmpty() || !corrections.isEmpty();
    }

    public Set<String> affectedEventTypes() {
        var types = new java.util.HashSet<String>();
        deletions.forEach(d -> types.add(d.targetEventType()));
        corrections.forEach(c -> types.add(c.targetEventType()));
        corrections.forEach(c -> types.add(c.correctedEventType()));
        return types;
    }
}
