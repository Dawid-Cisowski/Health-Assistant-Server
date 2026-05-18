package com.healthassistant.audit.api.dto;

import java.time.Instant;

public record AuditEventEntry(
        String eventType,
        Instant occurredAt,
        String deviceId,
        String idempotencyKey
) {
}
