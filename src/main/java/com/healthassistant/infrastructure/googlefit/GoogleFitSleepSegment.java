package com.healthassistant.infrastructure.googlefit;

import java.time.Instant;

public record GoogleFitSleepSegment(
        Instant start,
        Instant end,
        Long sleepType
) {
}

