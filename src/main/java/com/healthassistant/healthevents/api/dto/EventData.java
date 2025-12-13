package com.healthassistant.healthevents.api.dto;

import java.time.Instant;
import java.util.Map;

public record EventData(
        String eventType,
        Instant occurredAt,
        Map<String, Object> payload
) {
}
