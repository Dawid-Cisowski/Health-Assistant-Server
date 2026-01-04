package com.healthassistant.healthevents.api.dto.events;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public record CompensationEventsStoredEvent(
        String deviceId,
        List<CompensationEventData> deletions,
        List<CompensationEventData> corrections
) {

    public record CompensationEventData(
            String compensationEventId,
            String targetEventId,
            String targetEventType,
            Set<LocalDate> affectedDates
    ) {}

    public boolean hasAnyCompensations() {
        return !deletions.isEmpty() || !corrections.isEmpty();
    }

    public Set<String> affectedEventTypes() {
        var types = new java.util.HashSet<String>();
        deletions.forEach(d -> types.add(d.targetEventType()));
        corrections.forEach(c -> types.add(c.targetEventType()));
        return types;
    }
}
