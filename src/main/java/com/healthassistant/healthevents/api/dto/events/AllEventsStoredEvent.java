package com.healthassistant.healthevents.api.dto.events;

import java.time.LocalDate;
import java.util.Set;

public record AllEventsStoredEvent(
        String deviceId,
        Set<LocalDate> affectedDates,
        Set<String> eventTypes
) {
}
