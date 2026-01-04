package com.healthassistant.healthevents;

import java.time.Instant;

record CompensationTargetInfo(
        String targetEventId,
        String targetEventType,
        Instant targetOccurredAt,
        String deviceId
) {}
