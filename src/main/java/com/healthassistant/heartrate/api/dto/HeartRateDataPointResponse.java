package com.healthassistant.heartrate.api.dto;

import java.time.Instant;

public record HeartRateDataPointResponse(
        Instant measuredAt,
        Integer avgBpm,
        Integer minBpm,
        Integer maxBpm,
        Integer samples
) {
}
