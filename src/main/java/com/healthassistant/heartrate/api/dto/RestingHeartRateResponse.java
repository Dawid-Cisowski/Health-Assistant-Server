package com.healthassistant.heartrate.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RestingHeartRateResponse(
        LocalDate date,
        Integer restingBpm,
        Instant measuredAt
) {
}
